package cogmac.drawjav.audio;

import java.io.*;
import javax.media.opengl.*;

import cogmac.drawjav.*;
import cogmac.drawjav.audio.*;
import cogmac.jav.JavConstants;


/**
 * @author decamp
 */
public class TestAudioPlayer {
    

    
    public static void main(String[] args) throws Exception {
        test2();
    }
    
    
    
    static void test1() throws Exception {
        File file           = new File("/workspace/decamp/code/gopdebate/resources_ext/oct18/oct18_debate_full.ts");
        FormatDecoder demux = FormatDecoder.openFile(file, false, 0L);
        StreamHandle stream = demux.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        
        demux.openStream(stream);
        
        for(int i = 0; i < 10; i++) {
            Packet packet = demux.readNext();
            
            if(packet != null) {
                System.out.println("Okay: " + packet.getStartMicros());
                packet.deref();
            }
        }
    }
    
    
    static void test2() throws Exception {
        File file     = new File("/workspace/decamp/code/gopdebate/resources_ext/oct18/oct18_debate_full.ts");
        FormatDecoder demux = FormatDecoder.openFile(file);
        
        StreamHandle stream = demux.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        
        AudioFormat srcFormat = stream.audioFormat();
        AudioFormat dstFormat = new AudioFormat(srcFormat.channels(), 44100, JavConstants.AV_SAMPLE_FMT_FLT);
        
        final AudioLinePlayer liner   = new AudioLinePlayer(dstFormat, null, 1024 * 512 * 2 * 2);
        final AudioResamplerPipe pipe = new AudioResamplerPipe(liner, srcFormat, dstFormat, 32);
        
        demux.openStream(stream);
        liner.playStart(System.currentTimeMillis() * 1000L + 100000L);
        
        new Thread() {
            public void run() {
                try{
                    Thread.sleep(3000L);
                    liner.playStop(System.currentTimeMillis() * 1000L + 100000L);
                    Thread.sleep(1000L);
                    liner.playStart(System.currentTimeMillis() * 1000L + 100000L);
                }catch(Exception ex) {}
            }
        }.start();
        
        
        for(int i = 0; i < 30000; i++) {
            Packet p = demux.readNext();
            if(p == null)
                continue;
            
            pipe.consume((AudioPacket)p);
            p.deref();
        }
    }

    
    static void test3() throws Exception {
        GLCapabilities glc = new GLCapabilities();
        GLCanvas canvas = new GLCanvas(glc);
        
            
        
    }
    
}
