/*
 * Copyright (c) 2016. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import bits.drawjav.*;
import bits.drawjav.pipe.AllPlayer;
import bits.jav.Jav;
import bits.microtime.*;

import java.io.File;


/**
 * @author Philip DeCamp
 */
public class AudioVideoPlayerTest {


    private static final File TEST_FILE = new File( "../../../jav/src/test/resources/test.mp4" );


    public static void main( String[] args ) throws Exception {
        testRealtime();
    }


    static void testRealtime() throws Exception {
        File file = TEST_FILE;

        final MemoryManager mem       = new PoolMemoryManager( -1, -1 );
        final PlayController playCont = PlayController.createRealtime();
        final ClockControl clock      = playCont.clock();
        final FormatReader reader     = FormatReader.openFile( TEST_FILE, true, 0, mem );

        reader.openStream( reader.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ) );
        reader.openStream( reader.stream( Jav.AVMEDIA_TYPE_AUDIO, 0 ) );
            
        final AllPlayer player = new AllPlayer( mem, playCont.clock(), reader, false );
        
        VideoWindow win = null;
        
        for( int i = 0; i < player.textureCount(); i++ ) {
            if( player.texture( i ) != null ) {
                win = new VideoWindow( playCont, player.texture( i ) );
            }
        }
        
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


            
}
