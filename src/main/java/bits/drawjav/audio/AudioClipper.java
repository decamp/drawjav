///*
// * Copyright (c) 2015. Massachusetts Institute of Technology
// * Released under the BSD 2-Clause License
// * http://opensource.org/licenses/BSD-2-Clause
// */
//
//package bits.drawjav.audio;
//
//import bits.drawjav.Pipe;
//import bits.jav.util.JavMem;
//import bits.jav.util.JavSampleFormat;
//import bits.microtime.*;
//
//import java.io.IOException;
//import java.nio.ByteBuffer;
//import java.util.List;
//import java.util.logging.Logger;
//
//
///**
// * Removes samples from audio packets that fall outside of playback range.
// * AudioPacketClipper will also reverse packets during backward playback.
// *
// * AudioClipper is not thread safe.
// *
// * @author Philip DeCamp
// */
//public class AudioClipper implements SyncClockControl, Pipe<AudioPacket> {
//
//    private static final Logger sLog = Logger.getLogger( AudioClipper.class.getName() );
//
//    private AudioAllocator mAlloc;
//    private AudioFormat    mFormat;
//
//    private final FullClock mClock = new FullClock( Clock.SYSTEM_CLOCK );
//
//    private long    mClipMicros = Long.MIN_VALUE;
//    private boolean mBackward   = false;
//
//
//    public AudioClipper( AudioAllocator optAlloc ) {
//        if( optAlloc == null ) {
//            mAlloc = new OneStreamAudioAllocator( 32, -1, 1204 * 4 );
//        } else {
//            mAlloc = optAlloc;
//            mAlloc.ref();
//        }
//    }
//
//
//    @Override
//    public synchronized Result process( AudioPacket packet, List<? super AudioPacket> out ) throws IOException {
//        if( packet == null ) {
//            return Result.DONE;
//        }
//
//        AudioPacket result = null;
//
//        if( !mBackward ) {
//            result = clipForward( packet, mClipMicros, mAlloc );
//        } else {
//            result = clipBackward( packet, mClipMicros, mAlloc );
//        }
//
//        if( result != null ) {
//            out.add( result );
//        }
//
//        return Result.DONE;
//    }
//
//    @Override
//    public synchronized void clear() {}
//
//    @Override
//    public synchronized void close() throws IOException {
//        mAlloc.deref();
//        mAlloc = null;
//    }
//
//    @Override
//    public synchronized boolean isOpen() {
//        return mAlloc != null;
//    }
//
//
//    @Override
//    public synchronized void clockStart( long execMicros ) {
//        mClock.clockStart( execMicros );
//    }
//
//    @Override
//    public synchronized void clockStop( long execMicros ) {
//        mClock.clockStop( execMicros );
//    }
//
//    @Override
//    public synchronized void clockSeek( long execMicros, long seekMicros ) {
//        mClock.clockSeek( execMicros, seekMicros );
//        mClipMicros = seekMicros;
//    }
//
//    @Override
//    public synchronized void clockRate( long execMicros, Frac rate ) {
//        boolean backward = rate.mNum < 0;
//        if( backward != mBackward ) {
//            mBackward = backward;
//            mClipMicros = mClock.fromMaster( execMicros );
//        }
//        mClock.clockRate( execMicros, rate );
//    }
//
//
//
//    public static AudioPacket clipForward( AudioPacket packet, long clipMicros, AudioAllocator alloc ) throws IOException {
//        long t0 = packet.startMicros();
//        long t1 = packet.stopMicros();
//
//        if( t0 >= clipMicros ) {
//            packet.ref();
//            return packet;
//        }
//        if( t1 <= clipMicros ) {
//            return null;
//        }
//
//
//        double p = (double)( clipMicros - t0 ) / ( t1 - t0 );
//        final int totalSamples = packet.nbSamples();
//        final int writeSamples = Math.max( 0, Math.min( totalSamples, (int)( ( 1.0 - p ) * totalSamples + 0.5 ) ) );
//
//        final AudioFormat format = packet.audioFormat();
//        final int sampFormat     = format.sampleFormat();
//        final int sampSize       = JavSampleFormat.getBytesPerSample( format.sampleFormat() );
//        final int chans          = format.channels();
//
//        AudioPacket ret = alloc.alloc( format, totalSamples );
//
//        if( writeSamples > 0 ) {
//            if( chans == 1 || !JavSampleFormat.isPlanar( sampFormat ) ) {
//                final int off = (totalSamples - writeSamples) * sampSize * chans;
//                final int len = writeSamples * sampSize * chans;
//                JavMem.copy( packet.dataElem( 0 ) + off, ret.dataElem( 0 ), len );
//            } else {
//                final int off = (totalSamples - writeSamples) * sampSize;
//                final int len = writeSamples * sampSize;
//                for( int i = 0; i < chans; i++ ) {
//                    JavMem.copy( packet.extendedDataElem( i ) + off,
//                                 ret.extendedDataElem( i ),
//                                 len );
//                }
//            }
//        }
//        ret.nbSamples( writeSamples );
//        ret.init( packet.stream(), format, clipMicros, t1 );
//        return ret;
//    }
//
//
//    public static AudioPacket clipBackward( AudioPacket packet, long clipMicros, AudioAllocator alloc ) throws IOException {
//        long t0 = packet.startMicros();
//        long t1 = packet.stopMicros();
//
//        if( t0 >= clipMicros ) {
//            return null;
//        }
//        if( t1 <= clipMicros ) {
//            packet.ref();
//            return packet;
//        }
//
//        double p = (double)( clipMicros - t0 ) / ( t1 - t0 );
//        final int totalSamples  = packet.nbSamples();
//        final int writeSamples  = Math.max( 0, Math.min( totalSamples, (int)(p * totalSamples + 0.5) ) );
//
//        final AudioFormat format = packet.audioFormat();
//        final int sampFormat     = format.sampleFormat();
//        final int sampSize       = JavSampleFormat.getBytesPerSample( format.sampleFormat() );
//        final int chans          = format.channels();
//
//        AudioPacket ret = alloc.alloc( format, totalSamples );
//        if( writeSamples != 0 ) {
//            if( chans == 1 || !JavSampleFormat.isPlanar( sampFormat ) ) {
//                ByteBuffer src = packet.javaBufElem( 0 );
//                ByteBuffer dst = packet.javaBufElem( 0 );
//                if( src == null || dst == null ) {
//                    throw new IOException( "Packet not backed by java allocated buffer." );
//                }
//                int srcOff = (int)(packet.dataElem( 0 ) - JavMem.nativeAddress( src ));
//                int dstOff = (int)(ret.dataElem( 0 ) - JavMem.nativeAddress( dst ));
//                copyBackward( src, srcOff, dst, dstOff, writeSamples, sampSize * chans );
//            } else {
//                for( int i = 0; i < chans; i++ ) {
//                    ByteBuffer src = packet.javaExtendedBufElem( i );
//                    ByteBuffer dst = packet.javaExtendedBufElem( i );
//                    if( src == null || dst == null ) {
//                        throw new IOException( "Packet not backed by java allocated buffer." );
//                    }
//                    int srcOff = (int)(packet.extendedDataElem( i ) - JavMem.nativeAddress( src ));
//                    int dstOff = (int)(ret.extendedDataElem( i ) - JavMem.nativeAddress( dst ));
//                    copyBackward( src, srcOff, dst, dstOff, writeSamples, sampSize );
//                }
//            }
//        }
//
//        ret.nbSamples( writeSamples );
//        ret.init( packet.stream(), format, packet.startMicros(), clipMicros );
//        return ret;
//    }
//
//
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
//
//
//}
