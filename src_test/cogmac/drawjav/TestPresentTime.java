package cogmac.drawjav;

import java.io.File;

import cogmac.jav.*;
import cogmac.jav.codec.*;
import cogmac.jav.format.*;
import cogmac.jav.util.Rational;


/**
 * @author decamp
 */
public class TestPresentTime {

    
    public static void main( String[] args ) throws Exception {
        test1();
    }
    
    
    
    static void test1() throws Exception {
        File file = new File( "resources_ext/video.mp4" );
        
        JavLibrary.init();
        JavFormatContext format = JavFormatContext.openInputFile( file );
        JavPacket packet = JavPacket.newInstance();

        JavStream stream = format.stream( 0 );
        JavCodecContext codec = stream.codecContext();
        codec.open( JavCodec.findDecoder( codec.codecId() ) );
        JavFrame frame = JavFrame.newAutoFrame();
        
        for( int i = 0; i < 10; i++ ) {
            format.readPacket( packet );
            
            if( packet.streamIndex() != 0 ) {
                i--;
                continue;
            }
            
            boolean hasFrame = codec.decodeVideo( packet, frame );
            if( !hasFrame ) {
                continue;
            }
            
            Rational tb = stream.timeBase();
            
            System.out.println( "Packet " + i + "   Frame: " + hasFrame );
            System.out.println( "  timebase:               " + tb );
            System.out.println( "  frame.pts:              " + ( frame.pts() * tb.num() / (double)tb.den() ) );
            System.out.println( "  packet.pts:             " + ( packet.presentTime() * tb.num() / (double)tb.den() ) );
            System.out.println( "  coded picture number:   " + frame.codedPictureNumber() );
            System.out.println( "  display picture number: " + frame.displayPictureNumber() );
            System.out.println( "  interlaced frame:       " + frame.interlacedFrame() );
            System.out.println( "  picture type:           " + frame.pictureType() );
        }
    }
    
    
}
