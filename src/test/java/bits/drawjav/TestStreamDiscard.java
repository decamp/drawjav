/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import bits.jav.Jav;
import bits.jav.codec.JavPacket;
import bits.jav.format.JavFormatContext;
import bits.jav.format.JavStream;
import bits.jav.util.Rational;

import java.io.*;


/**
 * @author Philip DeCamp
 */
public class TestStreamDiscard {

    private static final File TEST_FILE = new File( "resources_ext/video.mp4" );


    public static void main( String[] args ) throws Exception {
        test3();
    }


    static void test1() throws Exception {
        FormatDecoder dec = FormatDecoder.openFile( TEST_FILE );
        dec.openStream( dec.stream( 0 ) );
        dec.openStream( dec.stream( 1 ) );

        try {
            while( true ) {
                Packet packet = dec.readNext();
                if( packet == null ) {
                    continue;
                }

                System.out.println( packet.stream() + " : " + packet.startMicros() );
            }
        } catch( EOFException ignored ) {}
        dec.close();
    }


    static void test2() throws Exception {
        File file = new File( "resources_ext/video.ts" );
        FormatDecoder dec = FormatDecoder.openFile( file, true, 0L );

        for( int i: new int[]{ 0, 1, 2 } ) {
            dec.openStream( dec.stream( i ) );
        }

        Packet[] packs = new Packet[3];
        boolean closeVideo = false;

        for( int i = 0; i < 100; i++ ) {
            long t = 10000000L + i * 10000L;
            int c = 0;

            if( i == 50 ) {
                closeVideo = true;
                dec.closeStream( dec.stream( 0 ) );
            }

            if( !closeVideo ) {
                dec.seek( dec.stream( 0 ), t );
                packs[c++] = next( dec, dec.stream( 0 ) );
            }

            {
                dec.seek( dec.stream( 1 ), t );
                packs[c++] = next( dec, dec.stream( 1 ) );
                Packet p = packs[c-1];
//                System.out.println( p.stream() + "\t" + dec.stream( 1 ) );
            }

            dec.seekAll( t );
            packs[c++] = next( dec, null );

            for( int j = 0; j < packs.length; j++ ) {
                if( packs[j] != null ) {
                    System.out.print( (t - packs[j].startMicros() ) + "\t" );
                    packs[j].deref();
                    packs[j] = null;
                }
            }

            System.out.println();
        }
    }


    static void test3() throws Exception {
        Jav.init();

        File file = new File( "resources_ext/video.mp4" );
        JavFormatContext fmt = JavFormatContext.openInput( file );

        Rational micros = new Rational( 1, 1000000 );
        JavStream stream = fmt.stream( 0 );

        long second = Rational.rescaleQ( 1000000, micros, stream.timeBase() );
        System.out.println( "SECOND: " + second );
        System.out.println( "TB: " + stream.timeBase() );

        for( int i = 1; i < fmt.streamCount(); i++ ) {
            JavStream s = fmt.stream( i );
            s.discard( Jav.AVDISCARD_ALL );
        }

        JavPacket packet = JavPacket.alloc();

        while( true ) {
            if( fmt.readPacket( packet ) != 0 ) {
                System.exit( -1 );
            }
            System.out.println( packet.streamIndex() + "   " + packet.flags() + "   " + packet.dts() );
            if( packet.flags() != 0 ) {
                break;
            }
        }

        System.out.println( "===" );

        //fmt.seek( 0, 6465826950L, 0 );
        fmt.seek( 0, -2002L + 5 * second, 0 );
        fmt.readPacket( packet );
        System.out.println( packet.streamIndex() + "   " + packet.flags() + "   " + packet.dts() );
        //System.out.println( -2002 + 5 * second );


//        long t = stream.startTime() + 5 * second;
//        fmt.seek( 0, t, Jav.AVSEEK_FLAG_BACKWARD );
//        if( fmt.readPacket( packet ) != 0 ) {
//            System.exit( -1 );
//        }
//
//        System.out.println( packet.streamIndex() + "\t" + packet.flags() + "\t" + packet.dts() );
    }


    static Packet next( FormatDecoder dec, StreamHandle stream ) throws IOException {
        while( true ) {
            Packet p = dec.readNext();
            if( p != null ) {
                if( stream == null || stream.equals( p.stream() ) ) {
                    return p;
                }
            }
        }
    }

}
