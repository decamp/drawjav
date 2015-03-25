/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.audio;

import java.io.*;

import bits.drawjav.*;
import bits.drawjav.old.AudioResamplerPipe;
import bits.jav.Jav;
import bits.microtime.PlayController;


/**
 * @author decamp
 */
public class AudioLinePlayerTest {

    private static final File TEST_FILE = new File( "../../../jav/src/test/resources/test.mp4" );


    public static void main( String[] args ) throws Exception {
//        testDecode();
        testPlay();
    }


    static void testDecode() throws Exception {
        File file = TEST_FILE;
        FormatReader demux = FormatReader.openFile( file, false, 0L, null );
        Stream stream = demux.stream( Jav.AVMEDIA_TYPE_AUDIO, 0 );

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
        FormatReader demux = FormatReader.openFile( file );
        Stream stream = demux.stream( Jav.AVMEDIA_TYPE_AUDIO, 0 );
        demux.openStream( stream );

        StreamFormat srcFormat = stream.format();
        StreamFormat dstFormat = StreamFormat.createAudio( srcFormat.mChannels, 44100, Jav.AV_SAMPLE_FMT_FLT );
        System.out.println( srcFormat + " -> " + dstFormat );

        final PlayController play               = PlayController.createAuto();
        final PacketAllocator<DrawPacket> alloc = OneFormatAllocator.createPacketLimited( 32 );
        final AudioLinePlayer liner             = new AudioLinePlayer( dstFormat, play, 1024 * 512 );
        final AudioResamplerPipe pipe           = new AudioResamplerPipe( liner, dstFormat, alloc  );

        play.control().clockStart();
        
        new Thread() {
            public void run() {
                try {
                    Thread.sleep( 3000L );
                    play.control().clockStop( System.currentTimeMillis() * 1000L + 100000L );
                    Thread.sleep( 1000L );
                    play.control().clockStart( System.currentTimeMillis() * 1000L + 100000L );
                } catch( Exception ignored ) {}
            }
        }.start();


        try {
            for( int i = 0; i < 500; i++ ) {
                Packet p = demux.readNext();
                if( p == null ) {
                    continue;
                }

                DrawPacket ap = (DrawPacket)p;
                //System.out.println( ap.nbSamples() );
                pipe.consume( (DrawPacket)p );
                p.deref();
            }
        } catch( EOFException ex ) {
            System.out.println( "Completed all packets. " );
        }

        //System.exit( 0 );
    }

}
