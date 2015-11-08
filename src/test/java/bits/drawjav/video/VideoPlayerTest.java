package bits.drawjav.video;

import bits.drawjav.*;
import bits.drawjav.pipe.AvGraph;
import bits.drawjav.pipe.VideoPlayer;
import bits.jav.Jav;
import bits.microtime.*;

import java.io.File;


/**
 * @author Philip DeCamp
 */
public class VideoPlayerTest {


    private static final File TEST_FILE = new File( "../../../jav/src/test/resources/test.mp4" );


    public static void main( String[] args ) throws Exception {
//        testRealtime();
//        testStepping();
        testSteppingSeek();
    }


    static void testRealtime() throws Exception {
        File file = TEST_FILE;

        final MemoryManager mem       = new PoolMemoryManager( -1, -1 );
        final PlayController playCont = PlayController.createRealtime();
        final ClockControl clock      = playCont.clock();
        final FormatReader reader     = FormatReader.openFile( TEST_FILE, true, 0, mem );

        Stream sh = reader.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 );
        reader.openStream( sh );

        final VideoPlayer player = new VideoPlayer( mem, playCont.clock(), reader, false );
        VideoWindow win = new VideoWindow( playCont, player.texture() );


        clock.clockSeek( 0 );
        player.start();
        win.start();

        // Update playcontroller before we startThreadedMode clock so that master clock will be caught up.
        playCont.tick();
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


    static void testStepping() throws Exception {
        final MemoryManager mem       = new PoolMemoryManager( -1, -1 );
        final PlayController playCont = PlayController.createStepping( 0, 1000000L / 30L );
        final ClockControl clock      = playCont.clock();
        final FormatReader reader     = FormatReader.openFile( TEST_FILE, true, 0, mem );

        Stream sh = reader.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 );
        reader.openStream( sh );

        final VideoPlayer player = new VideoPlayer( mem, playCont.clock(), reader, true );


        Ticker ticker = new Ticker() {
            @Override
            public void tick() {
                playCont.tick();
                player.tick();
            }
        };

        final VideoWindow win = new VideoWindow( ticker, player.texture() );
        win.start();
        clock.clockStart();

        try {
            Thread.sleep( 2000L );
            System.out.println( "STOP" );
            playCont.control().clockStop();
            Thread.sleep( 2000L );
            System.out.println("SEEK");
            playCont.control().clockSeek( 100000L );
            Thread.sleep( 2000L );
            System.out.println("PLAY");
            playCont.control().clockStart();

            while( true ) {
                Thread.sleep( 5000L );
                System.out.println("SEEK");
                playCont.control().clockSeek( 10000L );
            }
        } catch( Exception ex ) {}
    }


    static void testSteppingSeek() throws Exception {
        final MemoryManager mem       = new PoolMemoryManager( -1, -1 );
        final PlayController playCont = PlayController.createStepping( 0, 1000000L / 30L );
        final ClockControl clock      = playCont.clock();
        final FormatReader reader     = FormatReader.openFile( TEST_FILE, true, 0, mem );

        Stream sh = reader.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 );
        reader.openStream( sh );

        final VideoPlayer player = new VideoPlayer( mem, playCont.clock(), reader, true );
        Ticker ticker = new Ticker() {
            @Override
            public void tick() {
                playCont.tick();
                player.tick();
            }
        };

        final VideoWindow win = new VideoWindow( ticker, player.texture() );
        win.start();
        clock.clockStart();

        try {
            Thread.sleep( 2000L );
            playCont.control().clockSeek( 0L );
        } catch( Exception ex ) {}
    }

}
