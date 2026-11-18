package dev.mobilecodex.app.p1;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Synthetic data only. Missing files and unrelated errors are not evidence of isolation. */
public final class ProbeChecks {
    private ProbeChecks() {}
    public static byte[] readPrefix(java.io.InputStream input,int limit) throws IOException {
        if(limit<1)throw new IllegalArgumentException("invalid_output_limit");
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] buffer=new byte[Math.min(1024,limit)];
        while(out.size()<limit) {
            int count=input.read(buffer,0,Math.min(buffer.length,limit-out.size()));
            if(count<0)break;
            if(count==0) {
                int value=input.read();if(value<0)break;out.write(value);
            }else out.write(buffer,0,count);
        }
        return out.toByteArray();
    }
    /** Linux/Android errno: only EPERM=1 and EACCES=13 prove a permission denial. */
    public static String errnoOutcome(int errno) {return errno==1||errno==13?"denied":"error";}
    public static String readOutcome(Path path,String expected) {
        try {return new String(Files.readAllBytes(path),java.nio.charset.StandardCharsets.UTF_8).equals(expected)?"matched":"mismatched";}
        catch(AccessDeniedException denied){return "denied";}
        catch(FileSystemException error){
            String reason=error.getReason();
            return reason!=null&&(reason.contains("EACCES")||reason.contains("EPERM")||reason.equals("Permission denied")||reason.equals("Operation not permitted"))?"denied":"error";
        }
        catch(IOException|SecurityException error){return "error";}
    }
}
