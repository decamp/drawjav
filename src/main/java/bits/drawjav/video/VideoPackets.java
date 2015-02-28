/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import java.awt.image.BufferedImage;
import java.nio.*;

import bits.jav.Jav;


/**
 * Some basic utils for messing with frame data.
 * 
 * @author decamp
 */
public class VideoPackets {

    public static void toArgb( VideoPacket frame, boolean flip, IntFrame out ) {
        int h = frame.height();
        if( !flip ) {
            toArgb( frame, 0, h, out );
        } else {
            toArgb( frame, h, 0, out );
        }
    }


    public static void toArgb( VideoPacket frame, int yStart, int yStop, IntFrame out ) {
        //final PictureFormat format = frame.pictureFormat();
        final int w = frame.width();
        final int h = ( yStart < yStop ) ? yStop - yStart : yStart - yStop;

        int pixFmt = frame.format();
        RowReader reader = readerFor( pixFmt );
        if( reader == null ) {
            throw new IllegalArgumentException( "Unsupported pixel format: " + pixFmt );
        }

        out.resize( w, h, w );
        final int[] row = out.mPix;
        final ByteBuffer inBuf = frame.javaBufElem( 0 );
        final int inOff = inBuf.position();
        final int inStride = frame.lineSize( 0 );
        int yOut = 0;

        if( yStart < yStop ) {
            for( int y = yStart; y < yStop; y++ ) {
                inBuf.position( inOff + inStride * y );
                reader.read( inBuf, w, row, w * yOut++ );
            }
        } else {
            for( int y = yStart - 1; y >= yStop; y-- ) {
                inBuf.position( inOff + inStride * y );
                reader.read( inBuf, w, row, w * yOut++ );
            }
        }
    }

    
    public static void toBufferedImage( VideoPacket frame, boolean flip, BufferedImage out, int[] optRow ) {
        int w = frame.width();
        int h = frame.height();
        if( w != out.getWidth() || h != out.getHeight() ) {
            throw new IllegalArgumentException( "Dimensions don't match." );
        }

        RowReader reader = readerFor( frame.format() );
        if( reader == null ) {
            throw new IllegalArgumentException( "Unsupported pixel format: " + frame.format() );
        }
        
        final int[] row = ( optRow != null && optRow.length >= w ) ? optRow : new int[w];
        final ByteBuffer inBuf = frame.javaBufElem( 0 );
        final int inOff = inBuf.position();
        final int inStride = frame.lineSize( 0 );
        
        if( !flip ) {
            for( int y = 0; y < h; y++ ) {
                inBuf.position( inOff + inStride * y );
                reader.read( inBuf, w, row, 0 );
                out.setRGB( 0, y, w, 1, row, 0, w );
            }
        } else {
            for( int y = 0; y < h; y++ ) {
                inBuf.position( inOff + inStride * y );
                reader.read( inBuf, w, row, 0 );
                out.setRGB( 0, h - y - 1, w, 1, row, 0, w );
            }
        }
    }
    
    

    public static void pow( double[] a, int off, int len, double exp ) {
        for( int i = off; i < off + len; i++ ) {
            a[i] = Math.pow( a[i], exp );
        }
    }


    public static void setAlpha( IntFrame frame, double[] alpha ) {
        final int w = frame.mWidth;
        final int h = frame.mHeight;
        final int s = frame.mStride;
        final int[] p = frame.mPix;

        int ip = 0;
        int ia = 0;

        for( int y = 0; y < h; y++ ) {
            for( int x = 0; x < w; x++ ) {
                int v = (int)(alpha[ia + x] * 255.0 + 0.5) & 0xFF;
                p[ip + x] = (p[ip + x] & 0xFFFFFF) | (v << 24);
            }

            ip += s;
            ia += w;
        }
    }


    public static void setAlpha( IntFrame frame, double[] alpha, IntFrame out ) {
        final int w = frame.mWidth;
        final int h = frame.mHeight;
        final int is = frame.mStride;
        final int os = out.mStride;

        final int[] ip = frame.mPix;
        final int[] op = out.mPix;

        int iip = 0;
        int iop = 0;
        int ia = 0;

        for( int y = 0; y < h; y++ ) {
            for( int x = 0; x < w; x++ ) {
                int v = (int)(alpha[ia + x] * 255.0 + 0.5) & 0xFF;
                op[iop + x] = (ip[iip + x] & 0xFFFFFF) | (v << 24);
            }

            iip += is;
            iop += os;
            ia += w;
        }
    }


    public static void clampDown( double[] arr, int off, int len, double min, double replace ) {
        for( int i = off; i < off + len; i++ ) {
            if( arr[i] < min ) {
                arr[i] = replace;
            }
        }
    }



    private VideoPackets() {}
    

    private static RowReader readerFor( int pixFmt ) {
        switch( pixFmt ) {
        case Jav.AV_PIX_FMT_BGR24:
            return ROW_BGR24;
        case Jav.AV_PIX_FMT_RGB24:
            return ROW_RGB24;
        case Jav.AV_PIX_FMT_BGRA:
            return ROW_BGRA32;
        case Jav.AV_PIX_FMT_RGBA:
            return ROW_RGBA32;
        case Jav.AV_PIX_FMT_ABGR:
            return ROW_ABGR32;
        case Jav.AV_PIX_FMT_ARGB:
            return ROW_ARGB32;
        default:
            return null;
        }
    }
    
    
    private interface RowReader {
        public void read( ByteBuffer in, int w, int[] out, int off );
    }
    
    
    private static final RowReader ROW_BGR24 = new RowReader() {
        public void read( ByteBuffer in, int w, int[] out, int off ) {
            for( int i = 0; i < w; i++ ) {
                int c = 0xFF000000;
                c |= ( in.get() & 0xFF );
                c |= ( in.get() & 0xFF ) << 8;
                c |= ( in.get() & 0xFF ) << 16;
                out[off+i] = c;
            }
        }
    };
    
    
    private static final RowReader ROW_RGB24 = new RowReader() {
        public void read( ByteBuffer in, int w, int[] out, int off ) {
            for( int i = 0; i < w; i++ ) {
                int c = 0xFF000000;
                c |= ( in.get() & 0xFF ) << 16;
                c |= ( in.get() & 0xFF ) << 8;
                c |= ( in.get() & 0xFF );
                out[off+i] = c;
            }
        }
    };
        

    private static final RowReader ROW_BGRA32 = new RowReader() {
        public void read( ByteBuffer in, int w, int[] out, int off ) {
            for( int i = 0; i < w; i++ ) {
                int c = in.get() & 0xFF;
                c |= ( in.get() & 0xFF ) << 8;
                c |= ( in.get() & 0xFF ) << 16;
                c |= ( in.get() & 0xFF ) << 24;
                out[off+i] = c;
            }
        }
    };
    
    
    private static final RowReader ROW_RGBA32 = new RowReader() {
        public void read( ByteBuffer in, int w, int[] out, int off ) {
            for( int i = 0; i < w; i++ ) {
                int c = ( in.get() & 0xFF ) << 16;
                c |= ( in.get() & 0xFF ) << 8;
                c |= ( in.get() & 0xFF );
                c |= ( in.get() & 0xFF ) << 24;
                out[off+i] = c;
            }
        }
    };
    
    
    private static final RowReader ROW_ARGB32 = new RowReader() {
        public void read( ByteBuffer in, int w, int[] out, int off ) {
            for( int i = 0; i < w; i++ ) {
                int c = ( in.get() & 0xFF ) << 24;
                c |= ( in.get() & 0xFF ) << 16;
                c |= ( in.get() & 0xFF ) <<  8;
                c |= ( in.get() & 0xFF );
                out[off+i] = c;
            }
        }
    };


    private static final RowReader ROW_ABGR32 = new RowReader() {
        public void read( ByteBuffer in, int w, int[] out, int off ) {
            for( int i = 0; i < w; i++ ) {
                int c = ( in.get() & 0xFF ) << 24;
                c |= ( in.get() & 0xFF );
                c |= ( in.get() & 0xFF ) << 8;
                c |= ( in.get() & 0xFF ) << 16;
                out[off+i] = c;
            }
        }
    };
   
}
