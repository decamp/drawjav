package bits.drawjav.video;

import java.io.File;
import javax.swing.JFrame;

import bits.drawjav.*;
import bits.jav.Jav;
import bits.microtime.PlayController;


/**
 * @author Philip DeCamp
 */
public class VideoPlayTest {
    
    public static void main( String[] args ) throws Exception {
        test1();
    }
    
    
    static void test1() throws Exception {
        JFrame frame = new JFrame();
        frame.setSize( 800, 600 );
        frame.setLocationRelativeTo( null );
        
        VideoPanel panel = new VideoPanel();
        frame.setContentPane( panel );
        frame.setVisible( true );
        
        File file = new File( "/code/bluefin/cannesamp/resources_ext/params_drwho/Dr Who Tweet Clip.mp4" );
        //File file = new File( "/code/bluefin/cannesamp/resources_ext/params_drwho/Doctor Who Primary Video.mp4" );
        //File file = new File( "/code/bluefin/cannesamp/resources_ext/params_nfl/mcdonalds_preroll.mp4" );

//        {
//            PlayController playCont = PlayController.newAutoInstance();
//            FormatDecoder decoder = FormatDecoder.openFile( file, true, 0L );
//            decoder.openStream( decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ) );
//
//            List<Packet> list = new ArrayList<Packet>();
//            while( true ) {
//                Packet p = decoder.readNext();
//                if( p != null ) {
//                    list.add( p );
//                    System.out.println( list.size() );
//                    if( list.size() > 64 ) {
//                        break;
//                    }
//                }
//            }
//        }
        
        {
            PlayController playCont = PlayController.newAutoInstance();
            FormatDecoder decoder = FormatDecoder.openFile( file, true, 0L );
            decoder.openStream( decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ) );

            PictureFormat fmt = new PictureFormat( 640, 480, Jav.AV_PIX_FMT_BGRA, new bits.jav.util.Rational( 1, 1 ) );
            
            RealtimeDriver driver = RealtimeDriver.newInstance( playCont, decoder, null );
            driver.seekWarmupMicros( 3000000L );
            driver.openVideoStream( decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ), fmt, panel );
//                                    new PictureFormat( -1, -1, Jav.AV_PIX_FMT_BGRA, new bits.jav.util.Rational( 1, 1 ) ), panel );

            playCont.control().setRate( 1.0 );
            playCont.control().seek( 100000L );
            driver.start();
            playCont.control().playStart();
        }
        
        Thread.sleep( 500L );
        System.gc();
        Thread.sleep( 500L );
        System.out.println( "OKAY" );
       
        
        
//        new DumbDriver( decoder, panel );
    }

}
