package bits.drawjav.audio;

import java.io.*;

import bits.drawjav.*;
import bits.jav.Jav;


/**
 * @author decamp
 */
public class TestAudioPlayer {

    private static final File TEST_FILE = new File( "../../../jav/src/test/resources/test.mp4" );


    public static void main( String[] args ) throws Exception {
//        testDecode();
        testPlay();
    }


    static void testDecode() throws Exception {
        File file = TEST_FILE;
        FormatDecoder demux = FormatDecoder.openFile( file, false, 0L );
        StreamHandle stream = demux.stream( Jav.AVMEDIA_TYPE_AUDIO, 0 );

        demux.openStream( stream );

        for( int i = 0; i < 20; i++ ) {
            Packet packet = demux.readNext();
            if( packet != null ) {
                System.out.println( "Okay: " + packet.startMicros() );
                //packet.deref();
            } else {
                i--;
            }
        }
    }


    static void testPlay() throws Exception {
        File file = TEST_FILE;
        FormatDecoder demux = FormatDecoder.openFile( file );
        StreamHandle stream = demux.stream( Jav.AVMEDIA_TYPE_AUDIO, 0 );
        demux.openStream( stream );

        AudioFormat srcFormat = stream.audioFormat();
        AudioFormat dstFormat = new AudioFormat( srcFormat.channels(), 44100, Jav.AV_SAMPLE_FMT_FLT );
        System.out.println( srcFormat + " -> " + dstFormat );

        final AudioAllocator alloc    = new OneStreamAudioAllocator( 32, -1, -1 );
        final AudioLinePlayer liner   = new AudioLinePlayer( dstFormat, null, 1024 * 512 );
        final AudioResamplerPipe pipe = new AudioResamplerPipe( liner, dstFormat, alloc  );

        liner.playStart( System.currentTimeMillis() * 1000L + 100000L );
        
        new Thread() {
            public void run() {
                try {
                    Thread.sleep( 3000L );
                    liner.playStop( System.currentTimeMillis() * 1000L + 100000L );
                    Thread.sleep( 1000L );
                    liner.playStart( System.currentTimeMillis() * 1000L + 100000L );
                } catch( Exception ignored ) {}
            }
        }.start();


        try {
            for( int i = 0; i < 500; i++ ) {
                Packet p = demux.readNext();
                if( p == null ) {
                    continue;
                }

                AudioPacket ap = (AudioPacket)p;
                //System.out.println( ap.nbSamples() );
                pipe.consume( (AudioPacket)p );
                p.deref();
            }
        } catch( EOFException ex ) {
            System.out.println( "Completed all packets. " );
        }

        //System.exit( 0 );
    }

}
