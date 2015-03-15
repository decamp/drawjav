package bits.drawjav.video;

import bits.drawjav.*;
import bits.drawjav.pipe.VideoPlayer;
import bits.jav.Jav;
import bits.microtime.*;

import java.io.File;


/**
 * @author Philip DeCamp
 */
public class TestVideoPlayer {


    private static final File TEST_FILE = new File( "../../../jav/src/test/resources/test.mp4" );


    public static void main( String[] args ) throws Exception {
        testRealtime();
//        testSynced();
//        testMultiRealtime();
//        testMultiSynced();
    }


    static void testRealtime() throws Exception {
        File file = TEST_FILE;

        final MemoryManager mem       = new PoolMemoryManager( -1, 1024 * 1024 * 16, -1, 1024 * 1024 * 256 );
        final PlayController playCont = PlayController.createRealtime();
        final ClockControl clock      = playCont.clock();
        final FormatReader reader     = FormatReader.openFile( TEST_FILE, true, 0, mem );

        Stream sh = reader.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 );
        reader.openStream( sh );

        final VideoPlayer player = new VideoPlayer( mem, playCont.clock(), reader );
        new VideoWindow( playCont, player.texture() );

        clock.clockSeek( 0 );
        player.start();
        clock.clockStart();

//        try {
//            Thread.sleep( 2000L );
//            System.out.println( "STOP" );
//            playCont.control().clockStop();
//            Thread.sleep( 2000L );
//            System.out.println("SEEK");
//            playCont.control().clockSeek( 100000L );
//            Thread.sleep( 2000L );
//            System.out.println("PLAY");
//            playCont.control().clockStart();
////
////            while( true ) {
////                Thread.sleep( 5000L );
////                System.out.println("SEEK");
////                playCont.control().seek( 10000000L );
////            }
//        } catch( Exception ex ) {}
    }


}
