package bits.drawjav.video;

import java.io.*;
import javax.swing.JFrame;

import bits.clocks.PlayController;
import bits.drawjav.*;
import bits.jav.Jav;


/**
 * @author decamp
 */
public class VideoPanelTest {
    
    
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
        
        File file = new File( "resources_ext/video.mp4" );
        PlayController playCont = PlayController.newAutoInstance();
        FormatDecoder decoder   = FormatDecoder.openFile( file, true, 0L );
        decoder.openStream( decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ) );
        
        RealtimeDriver driver   = RealtimeDriver.newInstance( playCont, decoder, null );
        driver.seekWarmupMicros( 3000000L );
        driver.openVideoStream( decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ), 
                                null, panel );
//                                new PictureFormat( -1, -1, Jav.AV_PIX_FMT_BGRA, new bits.jav.util.Rational( 1, 1 ) ), panel );

        playCont.control().setRate( 1.0 );
        playCont.control().seek( 100000L );
        driver.start();
        playCont.control().playStart();
        
//        new DumbDriver( decoder, panel );
    }
    
    

    @SuppressWarnings( { "unchecked", "rawtypes" } )
    private static final class DumbDriver extends Thread {

        private final Source mSource;
        private final Sink<Packet> mSink;
        
        
        public DumbDriver( Source source, Sink sink ) {
            mSource = source;
            mSink   = sink;
            start();
        }
        
        
        public void run() {
            while( true ) {
                try {
                    Thread.sleep( 25L );
                }catch( InterruptedException ex ) {}
                
                while( true ) {
                    try {
                        Packet packet = mSource.readNext();
                        if( packet != null ) {
                            mSink.consume( packet );
                            break;
                        }   
                    } catch( IOException ex ) {
                        return;
                    }
                }
            }
        }
        
    }
        

}
