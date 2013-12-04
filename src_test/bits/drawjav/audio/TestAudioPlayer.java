package bits.drawjav.audio;

import java.io.*;

import bits.drawjav.*;
import bits.jav.Jav;


/**
 * @author decamp
 */
public class TestAudioPlayer {


    public static void main( String[] args ) throws Exception {
        testPlay();
    }


    static void testDecode() throws Exception {
        File file = new File( "../jav/resources_ext/video.ts" );
        FormatDecoder demux = FormatDecoder.openFile( file, false, 0L );
        StreamHandle stream = demux.stream( Jav.AVMEDIA_TYPE_AUDIO, 0 );

        demux.openStream( stream );

        for( int i = 0; i < 10; i++ ) {
            Packet packet = demux.readNext();
            if( packet != null ) {
                System.out.println( "Okay: " + packet.startMicros() );
                packet.deref();
            } else {
                i--;
            }
        }
    }


    static void testPlay() throws Exception {
        File file = new File( "../jav/resources_ext/video.ts" );
        FormatDecoder demux = FormatDecoder.openFile( file );
        StreamHandle stream = demux.stream( Jav.AVMEDIA_TYPE_AUDIO, 0 );

        AudioFormat srcFormat = stream.audioFormat();
        AudioFormat dstFormat = new AudioFormat( srcFormat.channels(), 44100, Jav.AV_SAMPLE_FMT_FLT );
        //System.out.println( srcFormat.channels() + "\t" + dstFormat.channels() + "\t" + srcFormat.sampleFormat() + "\t" + dstFormat.sampleFormat() );
        
        final AudioLinePlayer liner   = new AudioLinePlayer( dstFormat, null, 1024 * 512 * 2 * 2 );
        final AudioResamplerPipe pipe = new AudioResamplerPipe( liner, srcFormat, dstFormat, 32 );
        
        demux.openStream( stream );
        liner.playStart( System.currentTimeMillis() * 1000L + 100000L );
        
        new Thread() {
            public void run() {
                try {
                    Thread.sleep( 3000L );
                    liner.playStop( System.currentTimeMillis() * 1000L + 100000L );
                    Thread.sleep( 1000L );
                    liner.playStart( System.currentTimeMillis() * 1000L + 100000L );
                } catch( Exception ex ) {}
            }
        }.start();


        for( int i = 0; i < 30000; i++ ) {
            Packet p = demux.readNext();
            if( p == null ) {
                continue;
            }
            pipe.consume( (AudioPacket)p );
            p.deref();
        }
    }

}