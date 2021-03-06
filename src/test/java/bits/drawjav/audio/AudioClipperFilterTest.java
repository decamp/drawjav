package bits.drawjav.audio;

import bits.drawjav.*;
import bits.drawjav.pipe.*;
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
public class AudioClipperFilterTest {

    private static final File TEST_FILE = new File( "../../../jav/src/test/resources/test.mp4" );


    public AudioClipperFilterTest() {
        Jav.init();
    }

    @Test
    public void testNonEmpty() throws IOException {
        File file = TEST_FILE;
        MemoryManager mem    = new PoolPerFormatMemoryManager( 128, 0 );
        FormatReader  reader = FormatReader.openFile( file );
        Stream stream = reader.stream( Jav.AVMEDIA_TYPE_AUDIO, 0 );
        reader.openStream( stream );

        AudioPacketClipper clipper = new AudioPacketClipper( null );
        DrawPacket p = null;
        while( p == null ) {
            p = (DrawPacket)reader.readNext();
        }

        clipper.clockSeek( 0, (p.startMicros() + p.stopMicros()) / 2 );

        int err;
        DrawPacket[] arr = { null };

        err = clipper.input( 0 ).offer( p );

        assertEquals( Pad.OKAY, err );
        err = clipper.output( 0 ).poll( arr );
        assertEquals( Pad.OKAY, err );

        DrawPacket clipped = arr[0];
        assertEquals( p.nbSamples() / 2, clipped.nbSamples() );
        assertEquals( ( p.startMicros() + p.stopMicros() ) / 2, clipped.startMicros() );
        assertEquals( p.stopMicros(), clipped.stopMicros() );

        clipper.clockRate( 0, new Frac( -1, 1 ) );
        err = clipper.input( 0 ).offer( p );
        assertEquals( Pad.OKAY, err );
        err = clipper.output( 0 ).poll( arr );
        assertEquals( Pad.OKAY, err );

        clipped = arr[0];
        assertEquals( p.nbSamples() / 2, clipped.nbSamples() );
        assertEquals( p.startMicros(), clipped.startMicros() );
        assertEquals( ( p.startMicros() + p.stopMicros() ) / 2, clipped.stopMicros() );
    }

    @Test
    public void testSmallEmpty() throws IOException {
        StreamFormat format = StreamFormat.createAudio( 1, 44100, Jav.AV_SAMPLE_FMT_NONE );
        DrawPacket p = DrawPacket.createEmpty( null );
        p.init( null, 0, 20000L, false );
        format.getAudioProperties( p );

        AudioPacketClipper clipper = new AudioPacketClipper( null );
        clipper.clockSeek( 0, (p.startMicros() + p.stopMicros()) / 2 );
        DrawPacket clipped;
        DrawPacket[] arr = { null };
        int err;

        err = clipper.input( 0 ).offer( p );
        assertEquals( Pad.OKAY, err );
        err = clipper.output( 0 ).poll( arr );
        assertEquals( Pad.OKAY, err );
        clipped = arr[0];

        long samps = Frac.multLong( clipped.stopMicros() - clipped.startMicros(), format.mSampleRate, 1000000 );
        assertEquals( samps, clipped.nbSamples() );
        assertEquals( ( p.startMicros() + p.stopMicros() ) / 2, clipped.startMicros() );
        assertEquals( p.stopMicros(), clipped.stopMicros() );

        clipper.clockRate( 0, new Frac( -1, 1 ) );
        err = clipper.input( 0 ).offer( p );
        assertEquals( Pad.OKAY, err );
        err = clipper.output( 0 ).poll( arr );
        assertEquals( Pad.OKAY, err );
        clipped = arr[0];

        samps = Frac.multLong( clipped.stopMicros() - clipped.startMicros(), format.mSampleRate, 1000000 );
        assertEquals( samps, clipped.nbSamples() );
        assertEquals( p.startMicros(), clipped.startMicros() );
        assertEquals( ( p.startMicros() + p.stopMicros() ) / 2, clipped.stopMicros() );
    }

    @Test
    public void testBigEmptyForward() throws IOException {
        StreamFormat format = StreamFormat.createAudio( 1, 44100, Jav.AV_SAMPLE_FMT_NONE );
        DrawPacket p = DrawPacket.createEmpty( null );
        p.init( null, 0, 320000L, false );
        format.getAudioProperties( p );

        AudioPacketClipper clipper = new AudioPacketClipper( null );
        clipper.clockSeek( 0, 10000L );
        DrawPacket clipped;
        DrawPacket[] arr = { null };

        assertEquals( Pad.OKAY, clipper.input( 0 ).offer( p ) );
        assertTrue( clipper.output( 0 ).status() == Pad.OKAY );
        assertEquals( Pad.OKAY, clipper.output( 0 ).poll( arr ) );
        assertEquals(  10000L, arr[0].startMicros() );
        assertEquals( 110000L, arr[0].stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.output( 0 ).status() == Pad.OKAY );
        assertEquals( Pad.OKAY, clipper.output( 0 ).poll( arr ) );
        clipped = arr[0];
        assertEquals( 110000L, clipped.startMicros() );
        assertEquals( 210000L, clipped.stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.output( 0 ).status() == Pad.OKAY );
        assertEquals( Pad.OKAY, clipper.output( 0 ).poll( arr ) );
        clipped = arr[0];
        assertEquals( 210000L, clipped.startMicros() );
        assertEquals( 310000L, clipped.stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.output( 0 ).status() == Pad.OKAY );
        assertEquals( Pad.OKAY, clipper.output( 0 ).poll( arr ) );
        clipped = arr[0];
        assertEquals( 310000L, clipped.startMicros() );
        assertEquals( 320000L, clipped.stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.output( 0 ).status() == Pad.FILL_UNIT );
    }

    @Test
    public void testBigEmptyBackward() throws IOException {
        StreamFormat format = StreamFormat.createAudio( 1, 44100, Jav.AV_SAMPLE_FMT_NONE );
        DrawPacket p = DrawPacket.createEmpty( null );
        p.init( null, 0, 320000L, false );
        format.getAudioProperties( p );

        AudioPacketClipper clipper = new AudioPacketClipper( null );
        clipper.clockSeek( 0, 310000L );
        clipper.clockRate( 0, new Frac( -1, 1 ) );
        DrawPacket clipped;
        DrawPacket[] arr = { null };

        assertEquals( Pad.OKAY, clipper.input( 0 ).offer( p ) );
        assertTrue( clipper.output( 0 ).status() == Pad.OKAY );
        assertEquals( Pad.OKAY, clipper.output( 0 ).poll( arr ) );
        assertEquals( 210000L, arr[0].startMicros() );
        assertEquals( 310000L, arr[0].stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.output( 0 ).status() == Pad.OKAY );
        assertEquals( Pad.OKAY, clipper.output( 0 ).poll( arr ) );
        clipped = arr[0];
        assertEquals( 110000L, clipped.startMicros() );
        assertEquals( 210000L, clipped.stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.output( 0 ).status() == Pad.OKAY );
        assertEquals( Pad.OKAY, clipper.output( 0 ).poll( arr ) );
        clipped = arr[0];
        assertEquals(  10000L, clipped.startMicros() );
        assertEquals( 110000L, clipped.stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.output( 0 ).status() == Pad.OKAY );
        assertEquals( Pad.OKAY, clipper.output( 0 ).poll( arr ) );
        clipped = arr[0];
        assertEquals(       0L, clipped.startMicros() );
        assertEquals(  10000L, clipped.stopMicros() );
        arr[0].deref();
        arr[0] = null;

        assertTrue( clipper.output( 0 ).status() == Pad.FILL_UNIT );
    }

}
