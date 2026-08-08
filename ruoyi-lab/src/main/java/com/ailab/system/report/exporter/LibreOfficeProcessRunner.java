package com.ailab.system.report.exporter;

import com.ailab.system.config.LabProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;

/** Executes LibreOffice without a shell and with one disposable profile per conversion. */
public final class LibreOfficeProcessRunner {
    private static final int MAX_LOG = 64 * 1024;
    private final LabProperties properties; private final List<String> executable; private final Cleanup cleanup;
    private final ProcessTreeController processes;
    public interface Cleanup { void clean(Path root) throws IOException; }
    public LibreOfficeProcessRunner(LabProperties properties) { this(properties, Arrays.asList(properties.getLibreOfficeExecutable())); }
    /** Test seam for a Java fake executable; production still passes one soffice executable argument. */
    public LibreOfficeProcessRunner(LabProperties properties, List<String> executable) { this(properties, executable, null); }
    public LibreOfficeProcessRunner(LabProperties properties, List<String> executable, Cleanup cleanup) {
        this(properties, executable, cleanup, new OshiProcessTreeController());
    }
    LibreOfficeProcessRunner(LabProperties properties, List<String> executable, Cleanup cleanup, ProcessTreeController processes) {
        if (properties == null || executable == null || executable.isEmpty()) throw new IllegalArgumentException("LibreOffice configuration is required");
        this.properties = properties; this.executable = new ArrayList<String>(executable); this.cleanup = cleanup == null ? new Cleanup() { @Override public void clean(Path root) throws IOException { delete(root); } } : cleanup; this.processes = processes;
    }
    public byte[] convert(byte[] word, String name) throws ReportExportException {
        if (word == null || word.length == 0) throw new ReportExportException("Word input is required", false);
        Path root = null; ExecutorService readers = null; Process process = null; ProcessTreeSession session = null; List<Object> tracked = null; boolean terminationDone = false; ReportExportException primary = null;
        try {
            Path configured = Paths.get(properties.getTempDirectory()).toAbsolutePath().normalize(); Files.createDirectories(configured);
            root = Files.createTempDirectory(configured, "lo-").toAbsolutePath().normalize(); requireInside(configured, root);
            Path profile = Files.createDirectory(root.resolve("profile")); Path input = root.resolve(safeName(name) + ".docx"); Path out = Files.createDirectory(root.resolve("out"));
            session = processes.open(profile.toUri().toASCIIString());
            Files.write(input, word, StandardOpenOption.CREATE_NEW);
            List<String> command = new ArrayList<String>(executable); command.add("--headless"); command.add("--nologo"); command.add("--nodefault"); command.add("--nofirststartwizard"); command.add("--nolockcheck"); command.add("-env:UserInstallation=" + profile.toUri().toASCIIString()); command.add("--convert-to"); command.add("pdf"); command.add("--outdir"); command.add(out.toString()); command.add(input.toString());
            try { process = new ProcessBuilder(command).directory(root.toFile()).start(); try { tracked = session.snapshot(process); Thread.sleep(100L); tracked.addAll(session.snapshot(process)); } catch (ReportExportException ex) { emergencyTerminate(process); throw ex; } } catch (IOException ex) { throw new ReportExportException("LibreOffice executable is unavailable", true, ex); }
            final Process started = process;
            readers = Executors.newFixedThreadPool(2); Future<String> stdout = readers.submit(() -> bounded(started.getInputStream())); Future<String> stderr = readers.submit(() -> bounded(started.getErrorStream()));
            long timeout = Math.max(1L, properties.getConversionTimeoutSeconds());
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) { session.terminate(process, tracked); terminationDone = true; throw new ReportExportException("LibreOffice conversion timed out", true); }
            String error = stderr.get(5, TimeUnit.SECONDS); String output = stdout.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) throw new ReportExportException("LibreOffice conversion failed: " + compact(error, output), true);
            Path pdf = out.resolve(safeName(name) + ".pdf").normalize(); requireInside(out, pdf);
            if (!Files.isRegularFile(pdf)) throw new ReportExportException("LibreOffice produced no PDF output", true);
            byte[] bytes = Files.readAllBytes(pdf); long maximum = Math.max(1024L, properties.getMaxUploadSizeBytes());
            if (bytes.length > maximum || !pdf(bytes)) throw new ReportExportException("LibreOffice produced an invalid PDF", true); return bytes;
        } catch (ReportExportException ex) { primary = ex; throw ex;
        } catch (Exception ex) { primary = new ReportExportException("LibreOffice conversion failed", true, ex); throw primary;
        } finally { if (readers != null) readers.shutdownNow(); if (process != null && !terminationDone) try { if (tracked == null || session == null) emergencyTerminate(process); else session.terminate(process, tracked); } catch (ReportExportException ex) { if (primary != null) primary.addSuppressed(ex); else throw ex; } if (root != null) try { cleanup.clean(root); } catch (IOException ex) { if (primary != null) primary.addSuppressed(ex); else throw new ReportExportException("Report conversion cleanup failed; temporary data may remain", true, ex); } }
    }
    private String bounded(InputStream input) throws IOException { try (InputStream stream=input; ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] buffer=new byte[4096]; for(int n;(n=stream.read(buffer))>=0;) if(out.size()<MAX_LOG) out.write(buffer,0,Math.min(n,MAX_LOG-out.size())); return new String(out.toByteArray(), StandardCharsets.UTF_8); } }
    interface ProcessTreeController { ProcessTreeSession open(String token) throws ReportExportException; }
    interface ProcessTreeSession { List<Object> snapshot(Process process) throws ReportExportException; void terminate(Process process, List<Object> tracked) throws ReportExportException; }
    /** Java-8 compatible OS process inventory keyed by the unique LibreOffice profile URI. */
    static final class OshiProcessTreeController implements ProcessTreeController {
        public ProcessTreeSession open(String token) throws ReportExportException { return new OshiProcessTreeSession(token); }
    }
    static final class OshiProcessTreeSession implements ProcessTreeSession {
        private OperatingSystem os; private String token; private java.util.Set<Integer> baseline, roots = new java.util.LinkedHashSet<Integer>();
        OshiProcessTreeSession(String token) throws ReportExportException { try { this.token = token; this.os = new SystemInfo().getOperatingSystem(); this.baseline = new java.util.HashSet<Integer>(all().keySet()); } catch (Exception ex) { throw new ReportExportException("Cannot inspect operating-system process inventory", true, ex); } }
        public List<Object> snapshot(Process ignored) throws ReportExportException { try { java.util.Map<Integer, OSProcess> all = all(); java.util.Set<Integer> ids = new java.util.LinkedHashSet<Integer>(roots); for (OSProcess value : all.values()) if (!baseline.contains(value.getProcessID()) && containsToken(value)) { roots.add(value.getProcessID()); ids.add(value.getProcessID()); } boolean changed; do { changed=false; for (OSProcess value : all.values()) if (ids.contains(value.getParentProcessID()) && ids.add(value.getProcessID())) changed=true; } while(changed); List<Object> result=new ArrayList<Object>(); result.addAll(ids); return result; } catch(Exception ex) { throw new ReportExportException("Cannot safely inspect LibreOffice process tree", true, ex); } }
        public void terminate(Process ignored, List<Object> tracked) throws ReportExportException { java.util.LinkedHashSet<Integer> ids=new java.util.LinkedHashSet<Integer>(); if(tracked!=null) for(Object value:tracked) ids.add((Integer)value); long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5L); int stable=0; try { if(!ignored.isAlive()){for(Object value:snapshot(ignored))ids.add((Integer)value);boolean live=false;for(Integer id:ids)if(alive(id)){live=true;break;}if(!live)return;} while(System.nanoTime()<deadline){ for(Object value:snapshot(ignored)) ids.add((Integer)value); java.util.List<Integer> leaves=new java.util.ArrayList<Integer>(ids); java.util.Collections.reverse(leaves); for(Integer id:leaves) if(alive(id.intValue())) kill(id.intValue()); if(ignored.isAlive()){ignored.destroy(); if(!ignored.waitFor(1,TimeUnit.SECONDS)) ignored.destroyForcibly();} Thread.sleep(200L); boolean live=false; for(Object value:snapshot(ignored)) {int id=((Integer)value).intValue();ids.add(id);if(alive(id))live=true;} for(Integer id:ids) if(alive(id.intValue())){live=true;break;} if(!live&&++stable>=2)return; stable=0; } for(Object value:snapshot(ignored))ids.add((Integer)value); for(Integer id:ids)if(alive(id.intValue()))kill(id.intValue()); Thread.sleep(200L); boolean finalLive=false; for(Object value:snapshot(ignored)){ids.add((Integer)value);if(alive((Integer)value))finalLive=true;} for(Integer id:ids)if(alive(id.intValue()))finalLive=true; if(!finalLive)return; throw new ReportExportException("LibreOffice process tree survived termination "+details(ids),true); } catch(InterruptedException ex){Thread.currentThread().interrupt();throw new ReportExportException("Interrupted while terminating LibreOffice",true,ex);} }
        private boolean containsToken(OSProcess value){String command=value.getCommandLine();return command!=null&&command.contains(token);}
        private String details(java.util.Set<Integer> ids){StringBuilder value=new StringBuilder();for(Integer id:ids){OSProcess p=os.getProcess(id);value.append("pid=").append(id).append(",ppid=").append(p==null?"?":p.getParentProcessID()).append(",alive=");try{value.append(alive(id));}catch(Exception ex){value.append(ex.getClass().getSimpleName());}value.append(';');}return value.toString();}
        private boolean alive(int pid) throws ReportExportException { if(System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("win")) return windowsAlive(pid); OSProcess process=os.getProcess(pid);return process!=null&&process.updateAttributes()&&process.getState()!=OSProcess.State.INVALID; }
        private boolean windowsAlive(int pid) throws ReportExportException { WinNT.HANDLE handle=Kernel32.INSTANCE.OpenProcess(0x1000,false,pid); if(handle==null||WinBase.INVALID_HANDLE_VALUE.equals(handle)){int error=Kernel32.INSTANCE.GetLastError();if(error==87)return false;throw new ReportExportException("Cannot verify LibreOffice process " + pid,true);} try{IntByReference code=new IntByReference();if(!Kernel32.INSTANCE.GetExitCodeProcess(handle,code))throw new ReportExportException("Cannot read LibreOffice process exit code",true);return code.getValue()==259;}finally{Kernel32.INSTANCE.CloseHandle(handle);} }
        private java.util.Map<Integer,OSProcess> all(){java.util.Map<Integer,OSProcess> result=new java.util.LinkedHashMap<Integer,OSProcess>(); for(OSProcess value:os.getProcesses())result.put(value.getProcessID(),value);return result;}
        private void kill(int pid) throws ReportExportException { if(pid<=0)throw new ReportExportException("Invalid LibreOffice process identifier",true); List<String> argv=new ArrayList<String>(); if(System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("win")){argv.add("taskkill");argv.add("/PID");argv.add(String.valueOf(pid));argv.add("/T");argv.add("/F");}else{argv.add("/bin/kill");argv.add("-KILL");argv.add(String.valueOf(pid));}try{Process killer=new ProcessBuilder(argv).start();if(!killer.waitFor(5,TimeUnit.SECONDS)){killer.destroyForcibly();throw new ReportExportException("Process-tree terminator timed out",true);}int exit=killer.exitValue();String out=read(killer.getInputStream());String err=read(killer.getErrorStream());if(exit!=0&&alive(pid))throw new ReportExportException("Process-tree terminator exit="+exit+" pid="+pid+" out="+out+" err="+err,true);}catch(IOException ex){throw new ReportExportException("Cannot start safe process-tree terminator",true,ex);}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new ReportExportException("Interrupted while terminating LibreOffice",true,ex);}}
        private String read(InputStream stream)throws IOException{try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[512];for(int n;(n=in.read(b))>0&&out.size()<2048;)out.write(b,0,Math.min(n,2048-out.size()));return new String(out.toByteArray(),StandardCharsets.UTF_8).replaceAll("[\\r\\n]+"," ");}}
    }
    private void emergencyTerminate(Process process) throws ReportExportException { try { process.destroy(); if (!process.waitFor(2, TimeUnit.SECONDS)) { process.destroyForcibly(); if (!process.waitFor(5, TimeUnit.SECONDS) || process.isAlive()) throw new ReportExportException("LibreOffice root process survived emergency termination", true); } } catch (InterruptedException ex) { Thread.currentThread().interrupt(); process.destroyForcibly(); throw new ReportExportException("Interrupted while terminating LibreOffice", true, ex); } }
    private void requireInside(Path parent, Path child) throws ReportExportException { if (!child.toAbsolutePath().normalize().startsWith(parent.toAbsolutePath().normalize())) throw new ReportExportException("Unsafe conversion path", false); }
    private String safeName(String raw) { String value = raw == null ? "report" : raw.replaceAll("[^A-Za-z0-9._-]", "_"); value = value.replaceAll("^\\.+", ""); return value.isEmpty() ? "report" : value.length() > 80 ? value.substring(0,80) : value; }
    private boolean pdf(byte[] value) { return value.length >= 5 && value[0]=='%' && value[1]=='P' && value[2]=='D' && value[3]=='F' && value[4]=='-'; }
    private String compact(String stderr, String stdout) { String value=(stderr==null?"":stderr)+(stdout==null?"":" "+stdout); return value.replaceAll("[\\r\\n]+", " ").substring(0, Math.min(512, value.length())); }
    private void delete(Path root) throws IOException { IOException failure = null; for (int attempt = 0; attempt < 5; attempt++) { try { Files.walkFileTree(root, new SimpleFileVisitor<Path>() { @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.deleteIfExists(file); return FileVisitResult.CONTINUE; } @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException { if (error != null) throw error; Files.deleteIfExists(directory); return FileVisitResult.CONTINUE; } }); return; } catch (IOException ex) { failure = ex; try { Thread.sleep(100L); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; } } } throw failure == null ? new IOException("Temporary cleanup failed") : failure; }
}
