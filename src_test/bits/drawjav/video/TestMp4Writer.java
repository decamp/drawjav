package bits.drawjav.video;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;

import bits.jav.Jav;
import bits.jav.codec.JavCodec;
import bits.jav.codec.JavCodecContext;
import bits.jav.format.*;
import bits.jav.util.*;
import bits.math3d.SimplexNoise;


/**
* @author decamp
*/
public class TestMp4Writer {

    public static void main( String[] args ) throws Exception {
        test1();
    }


    static void test1() throws IOException {
        Jav.init();

        File file = new File( "/tmp/thing7.mp4" );
        if( file.exists() ) {
            file.delete();
        }

        final int w = 1920;
        final int h = 1080;

        Random rand = new Random();
        ByteBuffer buf = ByteBuffer.allocateDirect( w * h * 3 );

        Mp4Writer out = new Mp4Writer();
        out.size( w, h );
        out.gopSize( 300 );

//        out.quality( 30 );
        out.bitrate( 1024 * 1024 * 8 / 10 );
        out.open( file );

        for( int i = 0; i < 255; i++ ) {
            byte v = (byte)i;
            buf.clear();

            double ss = 1.0 / 50.0;
            double ts = 1.0 / 50.0;

            for( int y = 0; y < h; y++ ) {
                for( int x = 0; x < w; x++ ) {
                    byte r = (byte)( ( SimplexNoise.noise( x * ss, y * ss, i * ts ) + 1.0 ) * 128.0 );
                    buf.put( (byte)0xFF );
                    buf.put( v );
                    buf.put( r );
                }
            }
//
//            for( int j = 0; j < w*h; j++ ) {
//                byte r = (byte)rand.nextInt();
//                buf.put( (byte)0xFF );
//                buf.put( v );
//                buf.put( r );
//            }
            buf.flip();

            out.write( buf, w * 3 );
        }

        out.close();
    }


    static void testOptions() throws Exception {
        Jav.init();

        JavFormatContext fc     = JavFormatContext.openOutput( new File( "/tmp/tmpfile" ), null, "mp4" );
        JavCodec codec         = JavCodec.findEncoder( Jav.AV_CODEC_ID_H264 );
        JavStream stream       = fc.newStream( codec );
        JavCodecContext cc     = stream.codecContext();

        System.out.println( cc.codecId() + "\t" + Jav.AV_CODEC_ID_H264 );
        System.out.println( cc.codecName() );
        System.out.println( cc.codecType() + "\t" + Jav.AVMEDIA_TYPE_VIDEO );

        cc.width( 640 );
        cc.height( 480 );
        cc.timeBase( new Rational( 1, 1000000 ) );
        cc.gopSize( 25 );
        cc.maxBFrames( 0 );
        cc.pixelFormat( Jav.AV_PIX_FMT_YUV420P );

//        JavCodec codec = JavCodec.findEncoder( Jav.AV_CODEC_ID_H264 );
//        JavCodecContext cc = JavCodecContext.createAuto();
//        cc.open( codec );

//        List<JavOption> opts = JavOption.findOptions( cc );
//        for( JavOption opt: opts ) {
//            System.out.println();
//            System.out.println( opt.toDescription() );
//        }
//
//        System.out.println( "=========" );
        JavClass priv = cc.privData();
        JavOption opt = JavOption.nextOption( priv, null );
        while( opt != null ) {
            System.out.println();
            System.out.println( opt.toDescription() );
            opt = JavOption.nextOption( priv, opt );
        }

    }

}
