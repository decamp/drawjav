/*
* Copyright (c) 2015. Massachusetts Institute of Technology
* Released under the BSD 2-Clause License
* http://opensource.org/licenses/BSD-2-Clause
*/

package bits.drawjav.pipe;

import bits.drawjav.Packet;
import bits.drawjav.StreamHandle;
import bits.drawjav.audio.*;
import bits.jav.Jav;
import bits.jav.util.JavMem;
import bits.jav.util.JavSampleFormat;
import bits.microtime.*;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;


/**
* Removes samples from audio packets that fall outside of playback range.
* AudioPacketClipper will also reverse packets during backward playback.
*
* AudioClipper is not thread safe.
*
* @author Philip DeCamp
*/
public class AudioPacketClipper implements SyncClockControl, Filter {

    private static final Logger sLog = Logger.getLogger( AudioPacketClipper.class.getName() );

    public static final long    EMPTY_PACKET_MICROS  = 1000000L;
    private static final boolean SPLIT_EMPTY_PACKETS = true;

    private boolean mOpen = true;
    private AudioAllocator mAlloc;
    private AudioAllocator mEmptyAlloc;
    private AudioFormat    mFormat;

    private final SinkPad   mSink   = new Sink();
    private final SourcePad mSource = new Source();

    private final FullClock mClock = new FullClock( Clock.SYSTEM_CLOCK );

    private long    mClipMicros = Long.MIN_VALUE;
    private boolean mForward    = true;

    private AudioPacket  mOutPacket;
    // Indicates outgoing packet has no samples. Interpret as silence or no data.
    private boolean      mOutIsEmpty;
    // Used to cut up large packets when mOutIsEmpty == true (which means mOutPacket has no samples)
    private StreamHandle mOutStream;
    private AudioFormat  mOutFormat;
    private long         mOutStart;
    private long         mOutPos;
    private long         mOutStop;


    public AudioPacketClipper( AudioAllocator optFullAlloc, AudioAllocator optEmptyAlloc ) {
        if( optFullAlloc == null ) {
            mAlloc = new OneStreamAudioAllocator( 32, -1, 1204 * 4 );
        } else {
            mAlloc = optFullAlloc;
            mAlloc.ref();
        }

        if( optEmptyAlloc == null ) {
            mEmptyAlloc = new OneStreamAudioAllocator( 32, -1, 0 );
        } else {
            mEmptyAlloc = optEmptyAlloc;
            mEmptyAlloc.ref();
        }
    }


    @Override
    public int sinkNum() {
        return 1;
    }

    @Override
    public SinkPad sink( int idx ) {
        return mSink;
    }

    @Override
    public int sourceNum() {
        return 1;
    }

    @Override
    public SourcePad source( int idx ) {
        return mSource;
    }

    @Override
    public void clear() {
        if( mOutPacket != null ) {
            mOutPacket.deref();
            mOutPacket = null;
        }
    }

    @Override
    public void close() throws IOException {
        if( !mOpen ) {
            return;
        }
        mOpen = false;
        mAlloc.deref();
        mAlloc = null;
        mEmptyAlloc.deref();
        mEmptyAlloc = null;
        clear();
    }

    @Override
    public synchronized boolean isOpen() {
        return mOpen;
    }


    public synchronized FilterErr process( AudioPacket packet, List<? super AudioPacket> out ) throws IOException {
        if( packet == null ) {
            return FilterErr.DONE;
        }

        AudioPacket result = null;
        if( mForward ) {
            result = clipForward( packet, mClipMicros, mAlloc );
        } else {
            result = clipBackward( packet, mClipMicros, mAlloc );
        }

        if( result != null ) {
            out.add( result );
        }

        return FilterErr.DONE;
    }


    @Override
    public synchronized void clockStart( long execMicros ) {
        mClock.clockStart( execMicros );
    }

    @Override
    public synchronized void clockStop( long execMicros ) {
        mClock.clockStop( execMicros );
    }

    @Override
    public synchronized void clockSeek( long execMicros, long seekMicros ) {
        mClock.clockSeek( execMicros, seekMicros );
        mClipMicros = seekMicros;
    }

    @Override
    public synchronized void clockRate( long execMicros, Frac rate ) {
        boolean forward = rate.mNum >= 0;
        if( mForward != forward ) {
            mForward = forward;
            mClipMicros = mClock.fromMaster( execMicros );
        }
        mClock.clockRate( execMicros, rate );
    }



    private void initEmptyOutput( AudioPacket ref ) {
        mOutIsEmpty = true;
        mOutStart   = ref.startMicros();
        mOutPos     = mClipMicros;
        mOutStop    = ref.stopMicros();
        mOutStream  = ref.stream();
        mOutFormat  = ref.audioFormat();
        createNextEmptyOutPacket();
    }


    private boolean createNextEmptyOutPacket() {
        long t0;
        long t1;
        if( mForward ) {
            t0 = mOutPos;
            t1 = Math.min( mOutStop, mOutPos + EMPTY_PACKET_MICROS );
            if( t1 <= t0 ) {
                return false;
            }
            mOutPos = t1;
        } else {
            t0 = Math.max( mOutStart, mOutPos - EMPTY_PACKET_MICROS );
            t1 = mOutPos;
            if( t1 <= t0 ) {
                return false;
            }
            mOutPos = t0;
        }

        mOutPacket = mEmptyAlloc.alloc( mOutFormat, 0 );
        mOutPacket.init( mOutStream, mOutFormat, t0, t1 );
        return true;
    }


    private class Sink implements SinkPad {
        @Override
        public FilterErr offer( Packet packet, long blockMicros ) {
            if( packet == null ) {
                return FilterErr.DONE;
            }

            if( mOutPacket != null ) {
                return FilterErr.OVERFLOW;
            }

            AudioPacket p = (AudioPacket)packet;
            if( p.audioFormat().sampleFormat() != Jav.AV_SAMPLE_FMT_NONE ) {
                if( mForward ) {
                    mOutPacket = clipForward( p, mClipMicros, mAlloc );
                } else {
                    mOutPacket = clipBackward( p, mClipMicros, mAlloc );
                }
                mOutIsEmpty = false;
                return FilterErr.DONE;
            }

            if( !SPLIT_EMPTY_PACKETS ) {
                if( mForward ) {
                    mOutPacket = clipForward( p, mClipMicros, mEmptyAlloc );
                } else {
                    mOutPacket = clipBackward( p, mClipMicros, mEmptyAlloc );
                }
                mOutIsEmpty = false;
                return FilterErr.DONE;
            }

            // Empty packet.
            initEmptyOutput( p );
            return FilterErr.DONE;
        }

        @Override
        public int available() {
            return mOutPacket == null ? 1 : 0;
        }

        @Override
        public Exception exception() {
            return null;
        }
    }


    private class Source implements SourcePad {
        @Override
        public FilterErr remove( Packet[] out, long blockMicros ) throws IOException {
            if( mOutPacket == null ) {
                return FilterErr.UNDERFLOW;
            }

            out[0] = mOutPacket;
            mOutPacket = null;
            if( mOutIsEmpty ) {
                createNextEmptyOutPacket();
            }
            return FilterErr.DONE;
        }

        @Override
        public int available() {
            return mOutPacket == null ? 0 : 1;
        }

        @Override
        public Exception exception() {
            return null;
        }

    }



    public static AudioPacket clipForward( AudioPacket packet, long clipMicros, AudioAllocator alloc ) {
        long t0 = packet.startMicros();
        long t1 = packet.stopMicros();

        if( t0 >= clipMicros ) {
            packet.ref();
            return packet;
        }
        if( t1 <= clipMicros ) {
            return null;
        }

        double p = (double)( clipMicros - t0 ) / ( t1 - t0 );
        final int totalSamples = packet.nbSamples();
        final int writeSamples = Math.max( 0, Math.min( totalSamples, (int)( ( 1.0 - p ) * totalSamples + 0.5 ) ) );

        final AudioFormat format = packet.audioFormat();
        final int sampFormat     = format.sampleFormat();
        final int sampSize       = JavSampleFormat.getBytesPerSample( format.sampleFormat() );
        final int chans          = format.channels();

        AudioPacket ret = alloc.alloc( format, totalSamples );

        if( writeSamples > 0 ) {
            if( chans == 1 || !JavSampleFormat.isPlanar( sampFormat ) ) {
                final int off = (totalSamples - writeSamples) * sampSize * chans;
                final int len = writeSamples * sampSize * chans;
                JavMem.copy( packet.dataElem( 0 ) + off, ret.dataElem( 0 ), len );
            } else {
                final int off = (totalSamples - writeSamples) * sampSize;
                final int len = writeSamples * sampSize;
                for( int i = 0; i < chans; i++ ) {
                    JavMem.copy( packet.extendedDataElem( i ) + off,
                                 ret.extendedDataElem( i ),
                                 len );
                }
            }
        }
        ret.nbSamples( writeSamples );
        ret.init( packet.stream(), format, clipMicros, t1 );
        return ret;
    }


    public static AudioPacket clipBackward( AudioPacket packet, long clipMicros, AudioAllocator alloc ) {
        long t0 = packet.startMicros();
        long t1 = packet.stopMicros();

        if( t0 >= clipMicros ) {
            return null;
        }
        if( t1 <= clipMicros ) {
            packet.ref();
            return packet;
        }

        double p = (double)( clipMicros - t0 ) / ( t1 - t0 );
        final int totalSamples  = packet.nbSamples();
        final int writeSamples  = Math.max( 0, Math.min( totalSamples, (int)(p * totalSamples + 0.5) ) );

        final AudioFormat format = packet.audioFormat();
        final int sampFormat     = format.sampleFormat();
        final int sampSize       = JavSampleFormat.getBytesPerSample( format.sampleFormat() );
        final int chans          = format.channels();

        AudioPacket ret = alloc.alloc( format, totalSamples );
        if( writeSamples != 0 ) {
            if( chans == 1 || !JavSampleFormat.isPlanar( sampFormat ) ) {
                long src = packet.dataElem( 0 );
                long dst = packet.dataElem( 0 );
                JavMem.copyReverse( src, dst, writeSamples, sampSize * chans );
            } else {
                for( int i = 0; i < chans; i++ ) {
                    long src = packet.extendedDataElem( i );
                    long dst = packet.extendedDataElem( i );
                    JavMem.copyReverse( src, dst, writeSamples, sampSize );
                }
            }
        }

        ret.nbSamples( writeSamples );
        ret.init( packet.stream(), format, packet.startMicros(), clipMicros );
        return ret;
    }


//    public static void copyBackward( ByteBuffer src, int srcOff, ByteBuffer dst, int dstOff, int sampNum, int sampSize ) throws IllegalArgumentException {
//        src.clear();
//        dst.clear().position( dstOff );
//        srcOff += ( sampNum - 1 ) * sampSize;
//
//        switch( sampSize ) {
//        case 8:
//            for( int i = 0; i < sampNum; i++ ) {
//                dst.putLong( src.getLong( srcOff - i * sampSize ) );
//            }
//            break;
//
//        case 4:
//            for( int i = 0; i < sampNum; i++ ) {
//                dst.putInt( src.getInt( srcOff - i * sampSize ) );
//            }
//            break;
//
//        case 2:
//            for( int i = 0; i < sampNum; i++ ) {
//                dst.putShort( src.getShort( srcOff - i * sampSize) );
//            }
//            break;
//
//        case 1:
//            for( int i = 0; i < sampNum; i++ ) {
//                dst.put( src.get( srcOff - i * sampSize ) );
//            }
//            break;
//
//        default:
//            for( int i = 0; i < sampNum; i++ ) {
//                src.position( srcOff ).limit( srcOff + sampSize );
//                dst.put( src );
//                srcOff -= sampSize;
//            }
//        }
//    }

}
