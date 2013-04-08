package cogmac.drawjav;

import java.io.File;
import java.nio.ByteBuffer;

import cogmac.jav.*;
import cogmac.jav.codec.*;
import cogmac.jav.format.*;
import cogmac.jav.util.Rational;


/**
 * @author decamp
 */
public class TestPresentTime {

    
    public static void main(String[] args) throws Exception {
        test2();
    }
    
    
    
    static void test1() throws Exception {
        File file = new File("/workspace/decamp/code/gopdebate/resources_ext/oct18/oct18_debate_full.ts");
        JavLibrary.init();
        JavFormatContext format = JavFormatContext.openFile(file);
        JavPacket packet = JavPacket.newInstance();

        JavStream stream = format.stream(0);
        JavCodecContext codec = stream.codecContext();
        codec.openDecoder(JavCodec.findDecoder(codec.codecId()));
        
        JavFrame frame = JavFrame.newAutoFrame();
        
        for(int i = 0; i < 10; i++) {
            format.readPacket(packet);
            
            if(packet.streamIndex() != 0) {
                i--;
                continue;
            }
            
            codec.decodeVideo(packet, frame);
            
            
            Rational tb = stream.timeBase();
            
            System.out.print(packet.decodeTime() * tb.num() / (double)tb.den());
            System.out.print("  ");
            System.out.print(packet.presentTime() * tb.num() / (double)tb.den());
            System.out.print("  ");
            System.out.print((frame.pts() == JavConstants.AV_NOPTS_VALUE)); 
            System.out.println();
            //System.out.format("0x%016X\n", frame.presentTime());
            
        }
    }
    
    
    
    static void test2() throws Exception {
        File file = new File("/workspace/decamp/code/gopdebate/resources_ext/oct18/oct18_debate_full.ts");
        JavLibrary.init();
        JavFormatContext format = JavFormatContext.openFile(file);
        JavPacket packet = JavPacket.newInstance();

        JavStream stream       = format.stream(1);
        JavCodecContext codec = stream.codecContext();
        codec.openDecoder(JavCodec.findDecoder(codec.codecId()));
        
        ByteBuffer audioBuf = ByteBuffer.allocateDirect(JavConstants.AVCODEC_MAX_AUDIO_FRAME_SIZE);
        
        System.out.println(codec.sampleRate());
        
        for(int i = 0; i < 10; i++) {
            format.readPacket(packet);
            
            if(packet.streamIndex() != 1) {
                i--;
                continue;
            }
            
            audioBuf.clear();
            codec.decodeAudio(packet, audioBuf);
            
            Rational tb = stream.timeBase();
            
            System.out.print(packet.decodeTime() * tb.num() / (double)tb.den());
            System.out.print("  ");
            System.out.print(packet.presentTime() * tb.num() / (double)tb.den());
            System.out.println();
        }
    }


}
