/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 * This file might include comments and code snippets from FFMPEG, released under LGPL 2.1 or later.
 */

package bits.drawjav.audio;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;


public class WavWriter {

    private static final float FLOAT_TO_SHORT = (float)Short.MAX_VALUE;

    public static void write( float[][] inBufs, int sampleRate, File outputFile ) throws IOException {
        final int numBufs = inBufs.length;
        final int numSamples = inBufs[0].length;
        byte[] b = new byte[numBufs * numSamples * 2];
        ByteBuffer buf = ByteBuffer.wrap( b );
        float scale = Short.MAX_VALUE;
        int underSample = 0;
        int overSample = 0;


        for( int i = 0; i < numSamples; i++ ) {
            for( int j = 0; j < numBufs; j++ ) {
                float val = inBufs[j][i];

                if( val < -1f ) {
                    underSample++;
                    buf.putShort( Short.MIN_VALUE );

                } else if( val > 1f ) {
                    overSample++;
                    buf.putShort( Short.MAX_VALUE );

                } else {
                    buf.putShort( (short)(val * scale) );
                }
            }
        }

        System.out.println( "Overclipped samples: " + overSample );
        System.out.println( "Underclipped samples: " + underSample );
        System.out.println( "Writing to: " + outputFile.getPath() );

        javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat( sampleRate,
                                                                                      16,
                                                                                      numBufs,
                                                                                      true,
                                                                                      true );
        ByteArrayInputStream byteStream = new ByteArrayInputStream( b );
        AudioInputStream audioStream = new AudioInputStream( byteStream, format, numSamples * numBufs );
        AudioSystem.write( audioStream, AudioFileFormat.Type.WAVE, outputFile );
    }


    private final FileChannel mOut;
    private final ByteBuffer  mBuf;

    private final int mSampleBytes;
    private final int mSampleRate;

    private final long mRiffChunkSizePos;
    private final long mDataChunkSizePos;

    private int mLength = 0;
    private boolean mOpen = true;


    public WavWriter( File out, int channels, int rate ) throws IOException {
        mOut = new FileOutputStream( out ).getChannel();
        mBuf = ByteBuffer.allocateDirect( 4 * 1024 ).order( ByteOrder.LITTLE_ENDIAN );
        mSampleRate = rate;
        mSampleBytes = 2;

        writeInt( tag( "RIFF" ) );

        // Write zero as placeholder for chunk size. The correct size will be filled in when writer is closed.
        mRiffChunkSizePos = filePosition();
        writeInt( 0 );

        writeInt( tag( "WAVE" ) );
        writeInt( tag( "fmt " ) );
        writeInt( 16 );
        writeShort( (short)1 );
        writeShort( (short)channels );
        writeInt( rate );
        writeInt( rate * channels * mSampleBytes );
        writeShort( (short)(channels * mSampleBytes) );
        writeShort( (short)(mSampleBytes * 8) );
        writeInt( tag( "data" ) );

        // Write zero as placeholder for chunk size. The correct size will be filled in when writer is closed.
        mDataChunkSizePos = filePosition();
        writeInt( 0 );

        flush();
    }



    public void writeShorts( short[] buf, int off, int len ) throws IOException {
        while( len > 0 ) {
            int n = Math.min( len, mBuf.remaining() / 2 );
            if( n == 0 ) {
                flush();
                continue;
            }

            for( int i = 0; i < n; i++ ) {
                mBuf.putShort( buf[i + off] );
            }

            off += n;
            len -= n;
        }
    }


    public void writeFloats( float[] buf, int off, int len ) throws IOException {
        while( len > 0 ) {
            int n = Math.min( len, mBuf.remaining() / 2 );
            if( n == 0 ) {
                flush();
                continue;
            }

            for( int i = 0; i < n; i++ ) {
                mBuf.putShort( (short)(FLOAT_TO_SHORT * buf[i + off]) );
            }

            off += n;
            len -= n;
        }
    }


    public void writeFloats( ByteBuffer buf ) throws IOException {
        if( !mOpen ) {
            return;
        }

        int len = buf.remaining() / 4;
        while( len > 0 ) {
            int n = Math.min( len, mBuf.remaining() / 2 );

            if( n == 0 ) {
                flush();
                continue;
            }

            for( int i = 0; i < n; i++ ) {
                float samp = buf.getFloat();
                short s = (short)(FLOAT_TO_SHORT * samp);
                mBuf.putShort( s );
            }

            len -= n;
        }
    }


    public void close() throws IOException {
        if( !mOpen ) {
            return;
        }

        System.out.println( "### CLOSING" );
        mOpen = false;
        int payload = mSampleBytes * mLength;
        flush();

        mBuf.order( ByteOrder.LITTLE_ENDIAN );
        mBuf.putInt( 4 + 4 + 4 + 24 + 4 + 4 + payload );
        mBuf.flip();
        mOut.position( mRiffChunkSizePos );
        mOut.write( mBuf );

        mBuf.clear();
        mBuf.putInt( payload );
        mBuf.flip();
        mOut.position( mDataChunkSizePos );
        mOut.write( mBuf );

        mOut.close();
    }



    private void writeShort( short val ) throws IOException {
        if( mBuf.remaining() < 2 ) {
            flush();
        }
        mBuf.putShort( val );
    }


    private void writeInt( int val ) throws IOException {
        if( mBuf.remaining() < 4 ) {
            flush();
        }
        mBuf.putInt( val );
    }


    private int filePosition() {
        return mLength + mBuf.position();
    }


    private void flush() throws IOException {
        mBuf.flip();
        int rem = mBuf.remaining();
        while( mBuf.remaining() > 0 ) {
            mOut.write( mBuf );
        }
        mBuf.clear();
        mLength += rem;
    }


    private static int tag( String n ) {
        return (( n.charAt( 0 ) & 0xFF )       ) |
               (( n.charAt( 1 ) & 0xFF ) <<  8 ) |
               (( n.charAt( 2 ) & 0xFF ) << 16 ) |
               (( n.charAt( 3 ) & 0xFF ) << 24 );
    }

}

