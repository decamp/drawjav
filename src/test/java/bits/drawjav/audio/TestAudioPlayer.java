package bits.drawjav.audio;

import bits.drawjav.*;
import bits.drawjav.pipe.AudioPlayer;
import bits.jav.Jav;
import bits.microtime.*;

import java.io.File;
import java.util.Random;


/**
 * @author Philip DeCamp
 */
public class TestAudioPlayer {

//    private static final File TEST_FILE = new File( "../../../jav/src/test/resources/test.mp4" );
    private static final File TEST_FILE = new File( "../../ext/video.mp4" );

    public static void main( String[] args ) throws Exception {
        testPlay2();
    }

    static void testPlay() throws Exception {
        File file = TEST_FILE;
        MemoryManager mem   = new PoolMemoryManager( 128, -1, 0, 0 );
        FullClock clock     = new FullClock( Clock.SYSTEM_CLOCK );
        FormatReader reader = FormatReader.openFile( file );
        StreamHandle stream = reader.stream( Jav.AVMEDIA_TYPE_AUDIO, 0 );
        reader.openStream( stream );

        AudioPlayer player = new AudioPlayer( mem, clock, reader );
        Random rand = new Random( 1 );
        clock.clockStart();

        while( true ) {
            clock.clockSeek( rand.nextInt( 10000000 ) );
            Thread.sleep( 2000L );

            if( rand.nextBoolean() ) {
                clock.clockStop();
                Thread.sleep( 1000L );
                clock.clockStart();
            } else {
                float speed = rand.nextFloat() * 1.5f + 0.5f;
                Frac frac = new Frac();
                Frac.doubleToRational( speed, 100, frac );
                clock.clockRate( frac );
            }
        }
    }


    static void testPlay2() throws Exception {
        File file = TEST_FILE;
        MemoryManager mem   = new PoolMemoryManager( 128, -1, 0, 0 );
        FullClock clock     = new FullClock( Clock.SYSTEM_CLOCK );
        FormatReader reader = FormatReader.openFile( file );
        StreamHandle stream = reader.stream( Jav.AVMEDIA_TYPE_AUDIO, 0 );
        reader.openStream( stream );

        AudioPlayer player = new AudioPlayer( mem, clock, reader );
        Random rand = new Random( 1 );
        clock.clockStart();

        while( true ) {
            double speed = Math.pow( rand.nextFloat(), 1.5 ) * 5.5 + 0.25;
            Frac frac = new Frac();
            Frac.doubleToRational( speed, 100, frac );
            System.out.println( frac );
            clock.clockRate( frac );
            //clock.clockSeek( rand.nextInt( 10000000 ) );
            clock.clockSeek( 1500000L );
            Thread.sleep( 10000L );

            if( frac.mNum == 23 ) {
                break;
            }
        }
    }


}
