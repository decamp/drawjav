package bits.drawjav;

import java.io.File;

import bits.jav.*;
import bits.jav.codec.*;
import bits.jav.format.*;
import bits.jav.util.Rational;


/**
 * @author decamp
 */
public class PresentTimeTest {
    
    public static void main( String[] args ) throws Exception {
        test1();
    }
    
    
    static void test1() throws Exception {
        File file = new File( "resources_ext/video.mp4" );
        
        Jav.init();
        JavFormatContext format = JavFormatContext.openInput( file );
        JavPacket packet = JavPacket.alloc();

        JavStream stream = format.stream( 0 );
        JavCodecContext codec = stream.codecContext();
        codec.open( JavCodec.findDecoder( codec.codecId() ) );
        JavFrame frame = JavFrame.alloc();
        
        for( int i = 0; i < 10; i++ ) {
            format.readPacket( packet );
            
            if( packet.streamIndex() != 0 ) {
                i--;
                continue;
            }
            
            int[] gotFrame = { 0 };
            int n = codec.decodeVideo( packet, frame, gotFrame );
            if( gotFrame[0] == 0 ) {
                i--;
                continue;
            }
            
            Rational tb = stream.timeBase();
            
            System.out.println( "Packet " + i + "   Frame: " + gotFrame[0] );
            System.out.println( "  timestamp               " + frame.bestEffortTimestamp() );
            System.out.println( "  timebase:               " + tb );
            System.out.println( "  frame.pts:              " + ( frame.pts() * tb.num() / (double)tb.den() ) );
            System.out.println( "  packet.pts:             " + ( packet.pts() * tb.num() / (double)tb.den() ) );
            System.out.println( "  coded picture number:   " + frame.codedPictureNumber() );
            System.out.println( "  display picture number: " + frame.displayPictureNumber() );
            System.out.println( "  interlaced frame:       " + frame.interlacedFrame() );
            System.out.println( "  picture type:           " + frame.pictureType() );
        }
    }
    
}
