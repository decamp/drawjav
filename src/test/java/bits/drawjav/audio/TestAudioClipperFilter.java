package bits.drawjav.audio;

import bits.drawjav.*;
import bits.drawjav.pipe.AudioPacketClipper;
import bits.drawjav.pipe.FilterErr;
import bits.jav.Jav;
import bits.microtime.Frac;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * @author Philip DeCamp
 */
public class TestAudioClipperFilter {

    private static final File TEST_FILE = new File( "../../../jav/src/test/resources/test.mp4" );


    public TestAudioClipperFilter() {
        Jav.init();
    }

    @Test
    public void testNonEmpty() throws IOException {
        File file = TEST_FILE;
        MemoryManager mem    = new PoolMemoryManager( 128, -1, 0, 0 );
        FormatReader  reader = FormatReader.openFile( file );
        StreamHandle  stream = reader.stream( Jav.AVMEDIA_TYPE_AUDIO, 0 );
        reader.openStream( stream );

        AudioPacketClipper clipper = new AudioPacketClipper( null, null );
        AudioPacket p = null;
        while( p == null ) {
            p = (AudioPacket)reader.readNext();
        }

        clipper.clockSeek( 0, (p.startMicros() + p.stopMicros()) / 2 );

        FilterErr err;
        AudioPacket[] arr = { null };

        err = clipper.sink( 0 ).offer( p, 0 );
        assertEquals( FilterErr.DONE, err );
        err = clipper.source( 0 ).remove( arr, 0 );
        assertEquals( FilterErr.DONE, err );

        AudioPacket clipped = arr[0];
        assertEquals( p.nbSamples() / 2, clipped.nbSamples() );
        assertEquals( ( p.startMicros() + p.stopMicros() ) / 2, clipped.startMicros() );
        assertEquals( p.stopMicros(), clipped.stopMicros() );

        clipper.clockRate( 0, new Frac( -1, 1 ) );
        err = clipper.sink( 0 ).offer( p, 0 );
        assertEquals( FilterErr.DONE, err );
        err = clipper.source( 0 ).remove( arr, 0 );
        assertEquals( FilterErr.DONE, err );

        clipped = arr[0];
        assertEquals( p.nbSamples() / 2, clipped.nbSamples() );
        assertEquals( p.startMicros(), clipped.startMicros() );
        assertEquals( ( p.startMicros() + p.stopMicros() ) / 2, clipped.stopMicros() );
    }

    @Test
    public void testSmallEmpty() throws IOException {
        AudioFormat format = new AudioFormat( 1, 44100, Jav.AV_SAMPLE_FMT_NONE );
        AudioPacket p = AudioPacket.createAuto( null );
        p.init( null, format, 0, 1000000L );

        AudioPacketClipper clipper = new AudioPacketClipper( null, null );
        clipper.clockSeek( 0, (p.startMicros() + p.stopMicros()) / 2 );
        AudioPacket clipped;
        AudioPacket[] arr = { null };
        FilterErr err;

        err = clipper.sink( 0 ).offer( p, 0 );
        assertEquals( FilterErr.DONE, err );
        err = clipper.source( 0 ).remove( arr, 0 );
        assertEquals( FilterErr.DONE, err );
        clipped = arr[0];

        assertEquals( 0, clipped.nbSamples() );
        assertEquals( ( p.startMicros() + p.stopMicros() ) / 2, clipped.startMicros() );
        assertEquals( p.stopMicros(), clipped.stopMicros() );

        clipper.clockRate( 0, new Frac( -1, 1 ) );
        err = clipper.sink( 0 ).offer( p, 0 );
        assertEquals( FilterErr.DONE, err );
        err = clipper.source( 0 ).remove( arr, 0 );
        assertEquals( FilterErr.DONE, err );
        clipped = arr[0];

        assertEquals( 0, clipped.nbSamples() );
        assertEquals( p.startMicros(), clipped.startMicros() );
        assertEquals( ( p.startMicros() + p.stopMicros() ) / 2, clipped.stopMicros() );
    }

    @Test
    public void testBigEmptyForward() throws IOException {
        AudioFormat format = new AudioFormat( 1, 44100, Jav.AV_SAMPLE_FMT_NONE );
        AudioPacket p = AudioPacket.createAuto( null );
        p.init( null, format, 0, 3200000L );

        AudioPacketClipper clipper = new AudioPacketClipper( null, null );
        clipper.clockSeek( 0, 100000L );
        AudioPacket clipped;
        AudioPacket[] arr = { null };
        FilterErr err;

        assertEquals( FilterErr.DONE, clipper.sink( 0 ).offer( p, 0 ) );
        assertTrue( clipper.source( 0 ).available() > 0 );
        assertEquals( FilterErr.DONE, clipper.source( 0 ).remove( arr, 0 ) );
        assertEquals( 100000L, arr[0].startMicros() );
        assertEquals( 1100000L, arr[0].stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.source( 0 ).available() > 0 );
        assertEquals( FilterErr.DONE, clipper.source( 0 ).remove( arr, 0 ) );
        clipped = arr[0];
        assertEquals( 1100000L, clipped.startMicros() );
        assertEquals( 2100000L, clipped.stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.source( 0 ).available() > 0 );
        assertEquals( FilterErr.DONE, clipper.source( 0 ).remove( arr, 0 ) );
        clipped = arr[0];
        assertEquals( 2100000L, clipped.startMicros() );
        assertEquals( 3100000L, clipped.stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.source( 0 ).available() > 0 );
        assertEquals( FilterErr.DONE, clipper.source( 0 ).remove( arr, 0 ) );
        clipped = arr[0];
        assertEquals( 3100000L, clipped.startMicros() );
        assertEquals( 3200000L, clipped.stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.source( 0 ).available() == 0 );
    }

    @Test
    public void testBigEmptyBackward() throws IOException {
        AudioFormat format = new AudioFormat( 1, 44100, Jav.AV_SAMPLE_FMT_NONE );
        AudioPacket p = AudioPacket.createAuto( null );
        p.init( null, format, 0, 3200000L );

        AudioPacketClipper clipper = new AudioPacketClipper( null, null );
        clipper.clockSeek( 0, 3100000L );
        clipper.clockRate( 0, new Frac( -1, 1 ) );
        AudioPacket clipped;
        AudioPacket[] arr = { null };
        FilterErr err;

        assertEquals( FilterErr.DONE, clipper.sink( 0 ).offer( p, 0 ) );
        assertTrue( clipper.source( 0 ).available() > 0 );
        assertEquals( FilterErr.DONE, clipper.source( 0 ).remove( arr, 0 ) );
        assertEquals( 2100000L, arr[0].startMicros() );
        assertEquals( 3100000L, arr[0].stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.source( 0 ).available() > 0 );
        assertEquals( FilterErr.DONE, clipper.source( 0 ).remove( arr, 0 ) );
        clipped = arr[0];
        assertEquals( 1100000L, clipped.startMicros() );
        assertEquals( 2100000L, clipped.stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.source( 0 ).available() > 0 );
        assertEquals( FilterErr.DONE, clipper.source( 0 ).remove( arr, 0 ) );
        clipped = arr[0];
        assertEquals(  100000L, clipped.startMicros() );
        assertEquals( 1100000L, clipped.stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.source( 0 ).available() > 0 );
        assertEquals( FilterErr.DONE, clipper.source( 0 ).remove( arr, 0 ) );
        clipped = arr[0];
        assertEquals(       0L, clipped.startMicros() );
        assertEquals(  100000L, clipped.stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.source( 0 ).available() == 0 );
    }

}
