///*
//* Copyright (c) 2014. Massachusetts Institute of Technology
//* Released under the BSD 2-Clause License
//* http://opensource.org/licenses/BSD-2-Clause
//*/
//
//package bits.drawjav.video;
//
//import java.nio.*;
//
//
///**
//* Some basic utils for messing with frame data.
//*
//* @author decamp
//*/
//@Deprecated
//public class VideoUtil {
//
//    public static void javToIntFrame( VideoPacket frame, IntFrame out ) {
//        PictureFormat format = frame.pictureFormat();
//        int w = format.width();
//        int h = format.height();
//        int is = frame.lineSize( 0 );
//        if( is % 4 != 0 ) {
//            throw new IllegalArgumentException( "Row stride not divisible by 4" );
//        }
//
//        int[] pix = out.mPix;
//        IntBuffer buf = frame.directBuffer().asIntBuffer();
//
//        int os = is / 4;
//        if( pix == null || pix.length < os * h ) {
//            pix = new int[os * h];
//        }
//
//        buf.get( pix, 0, os * h );
//
//        out.mPix = pix;
//        out.mWidth = w;
//        out.mHeight = h;
//        out.mStride = os;
//    }
//
//
//    public static void javToFlippedIntFrame( VideoPacket frame, IntFrame out ) {
//        PictureFormat format = frame.pictureFormat();
//        int w = format.width();
//        int h = format.height();
//        int is = frame.lineSize( 0 );
//        if( is % 4 != 0 ) {
//            throw new IllegalArgumentException( "Row stride not divisible by 4" );
//        }
//
//        int[] pix = out.mPix;
//        IntBuffer buf = frame.directBuffer().asIntBuffer();
//
//        int os = is / 4;
//        if( pix == null || pix.length < os * h )
//            pix = new int[os * h];
//
//        for( int y = h - 1; y >= 0; y-- ) {
//            buf.get( pix, os * y, os );
//        }
//
//        out.mPix = pix;
//        out.mWidth = w;
//        out.mHeight = h;
//        out.mStride = os;
//    }
//
//
//    public static void javToIntFrame( VideoPacket frame, int y, int h, IntFrame out ) {
//        PictureFormat format = frame.pictureFormat();
//
//        int w = format.width();
//        int is = frame.lineSize( 0 );
//        int[] pix = out.mPix;
//        IntBuffer buf = frame.directBuffer().asIntBuffer();
//
//        if( is % 4 != 0 ) {
//            System.out.println( "Row stride not divisible by 4" );
//            System.exit( -1 );
//        }
//
//        buf.position( is * y / 4 );
//
//        int os = is / 4;
//        if( pix == null || pix.length < os * h ) {
//            pix = new int[os * h];
//        }
//
//        buf.get( pix, 0, os * h );
//        out.mPix = pix;
//        out.mWidth = w;
//        out.mHeight = h;
//        out.mStride = os;
//    }
//
//
//    public static double[] computeDifference( IntFrame a, IntFrame b, double[] out ) {
//        final int w = a.mWidth;
//        final int h = a.mHeight;
//        final int sa = a.mStride;
//        final int sb = b.mStride;
//        final int[] pa = a.mPix;
//        final int[] pb = b.mPix;
//
//        if( out == null || out.length < w * h )
//            out = new double[w * h];
//
//        int ia = 0;
//        int ib = 0;
//        int io = 0;
//
//        for( int y = 0; y < h; y++ ) {
//            for( int x = 0; x < w; x++ ) {
//                int va = pa[ia + x];
//                int vb = pb[ib + x];
//
//                int dr = ((va >> 16) & 0xFF) - ((vb >> 16) & 0xFF);
//                int dg = ((va >> 8) & 0xFF) - ((vb >> 8) & 0xFF);
//                int db = ((va) & 0xFF) - ((vb) & 0xFF);
//
//                out[io + x] = Math.sqrt( dr * dr + dg * dg + db * db ) / (255 * 3);
//            }
//
//            ia += sa;
//            ib += sb;
//            io += w;
//        }
//
//        return out;
//    }
//
//
//    public static void pow( double[] a, int off, int len, double exp ) {
//        for( int i = off; i < off + len; i++ ) {
//            a[i] = Math.pow( a[i], exp );
//        }
//    }
//
//
//    public static void setAlpha( IntFrame frame, double[] alpha ) {
//        final int w = frame.mWidth;
//        final int h = frame.mHeight;
//        final int s = frame.mStride;
//        final int[] p = frame.mPix;
//
//        int ip = 0;
//        int ia = 0;
//
//        for( int y = 0; y < h; y++ ) {
//            for( int x = 0; x < w; x++ ) {
//                int v = (int)(alpha[ia + x] * 255.0 + 0.5) & 0xFF;
//                p[ip + x] = (p[ip + x] & 0xFFFFFF) | (v << 24);
//            }
//
//            ip += s;
//            ia += w;
//        }
//    }
//
//
//    public static void setAlpha( IntFrame frame, double[] alpha, IntFrame out ) {
//        final int w = frame.mWidth;
//        final int h = frame.mHeight;
//        final int is = frame.mStride;
//        final int os = out.mStride;
//
//        final int[] ip = frame.mPix;
//        final int[] op = out.mPix;
//
//        int iip = 0;
//        int iop = 0;
//        int ia = 0;
//
//        for( int y = 0; y < h; y++ ) {
//            for( int x = 0; x < w; x++ ) {
//                int v = (int)(alpha[ia + x] * 255.0 + 0.5) & 0xFF;
//                op[iop + x] = (ip[iip + x] & 0xFFFFFF) | (v << 24);
//            }
//
//            iip += is;
//            iop += os;
//            ia += w;
//        }
//    }
//
//
//    public static void clampDown( double[] arr, int off, int len, double min, double replace ) {
//        for( int i = off; i < off + len; i++ ) {
//            if( arr[i] < min ) {
//                arr[i] = replace;
//            }
//        }
//    }
//
//
//    private VideoUtil() {}
//
//}