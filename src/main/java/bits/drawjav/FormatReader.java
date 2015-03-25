/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.util.*;

import bits.util.Guid;
import bits.jav.*;
import static bits.jav.Jav.*;

import bits.jav.codec.*;
import bits.jav.format.*;
import bits.jav.util.Rational;


/**
 * Not thread-safe.
 *
 * @bug When more than one stream is open, seek may miss some viable data
 * on one stream or another. This is quite hard to
 *
 *
 * @author decamp
 */
public class FormatReader implements PacketReader {

    public static FormatReader openFile( File file ) throws IOException {
        return openFile( file, false, 0L, null );
    }


    public static FormatReader openFile( File file,
                                         boolean overrideStartMicros,
                                         long startMicros,
                                         MemoryManager optMem )
                                         throws IOException
    {
        Jav.init();
        if( !file.exists() ) {
            throw new FileNotFoundException();
        }
        JavFormatContext format = JavFormatContext.openInput( file );
        return new FormatReader( format, overrideStartMicros, startMicros, optMem );
    }


    public static FormatReader open( JavFormatContext format ) throws IOException {
        return open( format, false, 0L, null );
    }


    public static FormatReader open( JavFormatContext format,
                                      boolean overrideStartMicros,
                                      long startMicros,
                                      MemoryManager optMem )
                                      throws IOException
    {
        return new FormatReader( format, overrideStartMicros, startMicros, optMem );
    }


    private static final Rational MICROS = new Rational( 1, 1000000 );


    private final JavFormatContext mFormat;
    private final MemoryManager    mMem;

    private final ReaderStream[] mStreams;
    private final ReaderStream[] mSeekStreams;
    private final ReaderStream   mEarliestStream;

    private final JavPacket mNullPacket;
    private       JavPacket mPacket;

    private boolean mPacketValid = false;
    private boolean mEof         = false;
    private int     mFlushStream = -1;

    private boolean mIsOpen = true;


    private FormatReader( JavFormatContext format,
                          boolean overrideStartMicros,
                          long startMicros,
                          MemoryManager optMem )
    {
        if( optMem == null ) {
            optMem = new PoolPerFormatMemoryManager( 64, 1024 * 1024 * 4, 32, 1024 * 1024 * 16 );
        }
        mMem = optMem;
        mFormat = format;
        mPacket = JavPacket.alloc();
        mNullPacket = JavPacket.alloc();
        mStreams = new ReaderStream[format.streamCount()];

        // Determine earliest timestamp in file.
        ReaderStream first = null;

        for( int i = 0; i < mStreams.length; i++ ) {
            JavStream js = format.stream( i );
            ReaderStream stream;

            switch( js.codecContext().codecType() ) {
            case AVMEDIA_TYPE_VIDEO:
                stream = new VideoStream( js );
                break;
            case AVMEDIA_TYPE_AUDIO:
                stream = new AudioStream( js );
                break;
            default:
                stream = new NullStream( js );
                break;
            }

            if( !overrideStartMicros ) {
                stream.initTimer( 0, 0 );
            } else if( first == null || stream.rawStartMicros() < first.rawStartMicros() ) {
                first = stream;
            }

            mStreams[i] = stream;
            js.discard( Jav.AVDISCARD_ALL );
        }

        mEarliestStream = first;
        if( overrideStartMicros ) {
            overrideTimestamps( startMicros );
        } else {
            useEmbeddedTimestamps( startMicros );
        }

        mSeekStreams = mStreams.clone();
        updateSeekStream();
    }


    public JavFormatContext formatContext() {
        return mFormat;
    }


    @Override
    public boolean isOpen() {
        return mIsOpen;
    }

    @Override
    public void close() throws IOException {
        if( !mIsOpen ) {
            return;
        }

        mIsOpen = false;
        for( ReaderStream s : mStreams ) {
            try {
                if( s.isOpen() ) {
                    s.close();
                }
            } catch( Exception ignored ) {}
        }
        mFormat.close();
        mPacket.deref();
        mNullPacket.deref();
        updateSeekStream();
    }


    @Override
    public int streamCount() {
        return mStreams.length;
    }

    @Override
    public Stream stream( int index ) {
        return mStreams[index];
    }


    public Stream stream( int mediaType, int index ) {
        for( ReaderStream s : mStreams ) {
            if( s.mFormat.mType == mediaType ) {
                if( index-- == 0 ) {
                    return s;
                }
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings( { "rawtypes", "unchecked" } )
    public List<Stream> streams() {
        return (List)Arrays.asList( mStreams );
    }


    public boolean isStreamOpen( Stream stream ) {
        return mStreams[((ReaderStream)stream).index()].isOpen();
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public void openStream( Stream stream ) throws IOException {
        assertOpen();
        ReaderStream ss = mStreams[((ReaderStream)stream).index()];
        if( ss.isOpen() ) {
            return;
        }

        switch( ss.mFormat.mType ) {
        case AVMEDIA_TYPE_AUDIO: {
            AudioStream s = (AudioStream)ss;
            s.open( mMem.allocator( stream.format() ) );
            updateSeekStream();
            return;
        }
        case AVMEDIA_TYPE_VIDEO: {
            VideoStream s = (VideoStream)ss;
            s.open( mMem.allocator( stream.format() ) );
            updateSeekStream();
            return;
        }
        default:
        }
    }

    @SuppressWarnings( "unchecked" )
    public void openStream( Stream stream, PacketAllocator alloc ) throws IOException {
        assertOpen();
        ReaderStream ss = mStreams[((ReaderStream)stream).index()];
        ss.open( alloc );
        updateSeekStream();
    }

    @Override
    public void closeStream( Stream stream ) throws IOException {
        ReaderStream ss = mStreams[((ReaderStream)stream).index()];
        if( !ss.isOpen() ) {
            return;
        }
        ss.close();
        updateSeekStream();
    }


    public boolean hasOpenStream() {
        return mSeekStreams.length > 0 && mSeekStreams[0].isOpen();
    }

    @Override
    public void seek( long micros ) throws IOException {
        if( mSeekStreams.length == 0 ) {
            return;
        }
        seek( mSeekStreams[0], micros );
    }


    public void seek( Stream stream, long micros ) throws IOException {
        assertOpen();

        if( stream == null ) {
            return;
        }

        ReaderStream s        = (ReaderStream)stream;
        Rational timeBase = s.timeBase();
        long     pts      = s.microsToPts( micros );

        preSeek();
        mFormat.seek( s.index(), pts, Jav.AVSEEK_FLAG_BACKWARD );
        postSeek( timeBase, pts );
    }

    /**
     * Performs seek across all open streams such that all data
     * may be decoded fully by the time {@code micros} is reached.
     * This is no different than a regular seek if only one stream
     * is open, but is more expensive and requires multiple index
     * queries otherwise.
     *
     * @param micros
     * @throws JavException
     */
    public void seekAll( long micros ) throws IOException {
        if( !hasOpenStream() ) {
            seek( micros );
            return;
        }

        ReaderStream firstStream = null;
        long firstPos      = Long.MAX_VALUE;
        preSeek();

        // mSeekStreams is ordered in likelihood of having the earliest packet required.
        // Seek through the streams from least to most likely to maximize possibility
        // of not needing to read last packet twice.
        for( int i = mSeekStreams.length - 1; i >= 0; i-- ) {
            ReaderStream s = mSeekStreams[i];
            if( !s.isOpen() ) {
                continue;
            }

            mFormat.seek( s.index(), s.microsToPts( micros ), Jav.AVSEEK_FLAG_BACKWARD );
            int err = mFormat.readPacket( mPacket );
            if( err != 0 ) {
                if( err != Jav.AVERROR_EOF ) {
                    throw new JavException( err );
                }
                s.mSeekResults = Long.MAX_VALUE;

            } else {
               long pos = mPacket.pos();
                if( pos <= firstPos ) {
                    firstStream = s;
                    firstPos    = pos;
                }
                s.mSeekResults = pos;
            }
        }

        if( firstStream == null ) {
            mEof = true;
            return;
        }

        if( firstStream == mSeekStreams[0] ) {
            // We are still in position, so no final seek is necessary.
            mPacketValid = true;
        } else {
            updateSeekStream();
            mFormat.seek( firstStream.index(), firstStream.microsToPts( micros ), Jav.AVSEEK_FLAG_BACKWARD );
        }

        postSeek( firstStream.timeBase(), firstStream.microsToPts( micros ) );
    }

    @Override
    public Packet readNext() throws IOException {
        assertOpen();

        if( mEof ) {
            return flush();
        }

        // Check if we still have packet.
        if( !mPacketValid ) {
            int err = mFormat.readPacket( mPacket );
            if( err != 0 ) {
                if( err == Jav.AVERROR_EOF ) {
                    mEof = true;
                    mFlushStream = 0;
                    return flush();
                } else {
                    throw new JavException( err );
                }
            }

            mPacketValid = true;
        }

        int idx = mPacket.streamIndex();
        if( idx < 0 || idx >= mStreams.length ) {
            mPacketValid = false;
            return null;
        }

        Packet ret = mStreams[idx].process( mPacket, false );
        mPacketValid = mPacket.size() > 0;
        return ret;
    }


    public void overrideTimestamps( long mediaStartMicros ) {
        ReaderStream first     = mEarliestStream;
        Rational firstTb = first.timeBase();
        long firstPts    = first.javStream().startTime();

        for( ReaderStream s: mStreams ) {
            long pts = s == first ? firstPts : Rational.rescaleQ( firstPts, s.timeBase(), firstTb );
            s.initTimer( pts, mediaStartMicros );
        }
    }


    public void useEmbeddedTimestamps( long offsetMicros ) {
        for( ReaderStream s : mStreams ) {
            s.initTimer( 0, offsetMicros );
        }
    }



    private void assertOpen() throws IOException {
        if( !mIsOpen ) {
            throw new ClosedChannelException();
        }
    }


    private void preSeek() {
        mEof         = false;
        mPacketValid = false;
        mFlushStream = -1;
    }


    private void postSeek( Rational timeBase, long pts ) {
        long micros = Rational.rescaleQ( pts, timeBase, MICROS );

        for( ReaderStream ss : mStreams ) {
            if( timeBase.equals( ss.timeBase() ) ) {
                ss.seekPts( pts );
            } else {
                ss.seekMicros( micros );
            }
        }
    }


    private ReaderStream updateSeekStream() {
        if( mSeekStreams.length == 0 ) {
            return null;
        }
        Arrays.sort( mSeekStreams, SEEK_PREFERENCE );
        return mSeekStreams[0];
    }


    private Packet flush() throws IOException {
        for( ; mFlushStream >= 0 && mFlushStream < mStreams.length; mFlushStream++ ) {
            Packet packet = mStreams[mFlushStream].process( mNullPacket, true );
            if( packet != null ) {
                return packet;
            }
        }

        mFlushStream = -1;
        throw new EOFException();
    }



    private static abstract class ReaderStream implements Stream {

        final JavStream    mStream;
        final StreamFormat mFormat;
        final Guid         mGuid;
        final int          mIndex;
        final Rational     mTimeBase;

        final long        mRawStartMicros;
        final PacketTimer mTimer;

        long mSeekResults = Long.MIN_VALUE;

        ReaderStream( JavStream stream ) {
            mStream = stream;
            mGuid   = Guid.create();
            mIndex  = stream.index();
            mFormat = StreamFormat.fromCodecContext( stream.codecContext() );

            mTimeBase       = stream.timeBase();
            mRawStartMicros = Rational.rescaleQ( stream.startTime(), mTimeBase, MICROS );
            mTimer          = new PacketTimer( mTimeBase );
        }

        @Override
        public StreamFormat format() {
            return mFormat;
        }


        public JavStream javStream() {
            return mStream;
        }


        public int index() {
            return mIndex;
        }


        public Rational timeBase() {
            return mTimeBase;
        }


        public long rawStartMicros() {
            return mRawStartMicros;
        }


        public void initTimer( long startSrcPts, long startDstMicros ) {
            mTimer.init( startSrcPts, startDstMicros );
        }

        @SuppressWarnings( "unused" )
        public long ptsToMicros( long pts ) {
            return mTimer.ptsToMicros( pts );
        }


        public long microsToPts( long micros ) {
            return mTimer.microsToPts( micros );
        }

        @Override
        public int hashCode() {
            return mGuid.hashCode();
        }


        public abstract void open( PacketAllocator<DrawPacket> alloc ) throws JavException;

        public abstract boolean isOpen();

        public abstract void close() throws IOException;

        abstract Packet process( JavPacket packet, boolean flushing ) throws IOException;

        abstract void seekPts( long pts );

        abstract void seekMicros( long micros );

    }


    private static class VideoStream extends ReaderStream {

        private final JavCodecContext mCodecContext;
        private final StreamFormat    mFormat;

        private final long[] mRange    = new long[2];
        private final int[]  mGotFrame = new int[1];

        private boolean                     mIsOpen       = false;
        private PacketAllocator<DrawPacket> mAlloc        = null;
        private boolean                     mHasKeyFrame  = false;
        private DrawPacket                  mCurrentFrame = null;


        VideoStream( JavStream stream ) {
            super( stream );
            mCodecContext = stream.codecContext();
            mFormat = StreamFormat.fromCodecContext( mCodecContext );
        }


        public void open( PacketAllocator<DrawPacket> alloc ) throws JavException {
            if( mIsOpen ) {
                return;
            }

            if( !mCodecContext.isOpen() ) {
                JavCodec codec = JavCodec.findDecoder( mCodecContext.codecId() );
                if( codec == null ) {
                    throw new JavException( Jav.AVERROR_DECODER_NOT_FOUND,
                                            "Codec not found for stream: " + mStream.index() );
                }
                mCodecContext.refcountedFrames( 1 );
                mCodecContext.open( codec );
            }

            mAlloc = alloc;
            alloc.ref();
            mStream.discard( Jav.AVDISCARD_DEFAULT );
            mIsOpen = true;
        }


        @Override
        public StreamFormat format() {
            return mFormat;
        }

        @Override
        public boolean isOpen() {
            return mIsOpen;
        }

        @Override
        public void close() {
            if( !mIsOpen ) {
                return;
            }

            mStream.discard( Jav.AVDISCARD_ALL );
            mIsOpen = false;
            if( mCodecContext.isOpen() ) {
                try {
                    mCodecContext.close();
                } catch( Exception ignored ) {
                }
            }

            if( mAlloc != null ) {
                mAlloc.deref();
                mAlloc = null;
            }

            mHasKeyFrame = false;
            if( mCurrentFrame != null ) {
                mCurrentFrame.deref();
                mCurrentFrame = null;
            }
        }

        @Override
        public Packet process( JavPacket packet, boolean flushing ) throws IOException {
            if( !mIsOpen ) {
                if( !flushing ) {
                    mTimer.packetSkipped( packet.pts(), packet.duration(), mRange );
                    packet.moveDataPointer( packet.size() );
                }
                return null;
            }

            DrawPacket ret = mCurrentFrame;
            mCurrentFrame = null;
            if( ret == null ) {
                ret = mAlloc.alloc( mFormat, 0 );
            }

            int n = mCodecContext.decodeVideo( packet, ret, mGotFrame );
            if( n < 0 ) {
                throw new JavException( n );
            } else if( !flushing ) {
                packet.moveDataPointer( n );
            }

            if( mGotFrame[0] == 0 ) {
                mCurrentFrame = ret;
                return null;
            }

            mTimer.packetDecoded( ret.bestEffortTimestamp(), ret.packetDuration(), mRange );
            if( !mHasKeyFrame && !ret.isKeyFrame() ) {
                ret.deref();
                return null;
            }

            mHasKeyFrame = true;
            ret.init( mFormat, mRange[0], mRange[1], false );
            ret.stream( this );

            return ret;
        }

        @Override
        public void seekPts( long pts ) {
            mTimer.seekPts( pts );
            if( mIsOpen ) {
                mCodecContext.flushBuffers();
            }
        }

        @Override
        public void seekMicros( long micros ) {
            mTimer.seekMicros( micros );
            if( mIsOpen ) {
                mCodecContext.flushBuffers();
            }
        }

    }


    private static class AudioStream extends ReaderStream {

        private final JavCodecContext mCodecContext;

        private final long[] mRange    = new long[2];
        private final int[]  mGotFrame = new int[1];

        private boolean                     mIsOpen       = false;
        private PacketAllocator<DrawPacket> mAlloc        = null;
        private DrawPacket                  mCurrentFrame = null;


        AudioStream( JavStream stream ) {
            super( stream );
            mCodecContext = stream.codecContext();
        }


        @Override
        public void open( PacketAllocator<DrawPacket> alloc ) throws JavException {
            if( mIsOpen ) {
                return;
            }
            if( !mCodecContext.isOpen() ) {
                JavCodec codec = JavCodec.findDecoder( mCodecContext.codecId() );
                if( codec == null ) {
                    throw new JavException( AVERROR_DECODER_NOT_FOUND,
                                            "Codec not found for stream: " + mStream.index() );
                }
                mCodecContext.refcountedFrames( 1 );
                mCodecContext.open( codec );
            }

            mAlloc = alloc;
            alloc.ref();
            mStream.discard( Jav.AVDISCARD_DEFAULT );
            mIsOpen = true;
        }

        @Override
        public boolean isOpen() {
            return mIsOpen;
        }

        @Override
        public void close() {
            if( !mIsOpen ) {
                return;
            }

            mIsOpen = false;
            mStream.discard( Jav.AVDISCARD_ALL );

            if( mAlloc != null ) {
                mAlloc.deref();
                mAlloc = null;
            }
            if( mCodecContext.isOpen() ) {
                try {
                    mCodecContext.close();
                } catch( Exception ignore ) {
                }
            }
        }

        @Override
        public DrawPacket process( JavPacket packet, boolean flushing ) throws IOException {
            if( mAlloc == null ) {
                if( !flushing ) {
                    mTimer.packetSkipped( packet.pts(), packet.duration(), mRange );
                    packet.moveDataPointer( packet.size() );
                }
                return null;
            }

            DrawPacket ret = mCurrentFrame;
            mCurrentFrame = null;

            if( ret == null ) {
                ret = mAlloc.alloc( mFormat, -1 );
            }

            int n = mCodecContext.decodeAudio( packet, ret, mGotFrame );
            if( n < 0 ) {
                throw new JavException( n );
            } else if( !flushing ) {
                packet.moveDataPointer( n );
            }
            if( mGotFrame[0] == 0 ) {
                mCurrentFrame = ret;
                return null;
            }

            mTimer.packetDecoded( ret.bestEffortTimestamp(), ret.packetDuration(), mRange );
            ret.init( mFormat, mRange[0], mRange[1], false );
            ret.stream( this );
            return ret;
        }

        @Override
        public void seekPts( long pts ) {
            mTimer.seekPts( pts );
            if( mIsOpen ) {
                mCodecContext.flushBuffers();
            }
        }

        @Override
        public void seekMicros( long micros ) {
            mTimer.seekMicros( micros );
            if( mIsOpen ) {
                mCodecContext.flushBuffers();
            }
        }

    }


    private static class NullStream extends ReaderStream {

        private final long[] mRange = new long[2];


        NullStream( JavStream stream ) {
            super( stream );
        }

        @Override
        public void open( PacketAllocator<DrawPacket> alloc ) throws JavException {}

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void close() {}

        @Override
        public Packet process( JavPacket packet, boolean flushing ) throws IOException {
            if( flushing ) {
                return null;
            }

            long pts = packet.pts();
            long duration = packet.duration();
            mTimer.packetSkipped( pts, duration, mRange );
            packet.moveDataPointer( packet.size() );

            return null;
        }

        @Override
        public void seekPts( long pts ) {
            mTimer.seekPts( pts );
        }

        @Override
        public void seekMicros( long micros ) {
            mTimer.seekMicros( micros );
        }

    }


    private static final Comparator<ReaderStream> SEEK_PREFERENCE = new Comparator<ReaderStream>() {
        @Override
        public int compare( ReaderStream a, ReaderStream b ) {
            if( a.isOpen() != b.isOpen() ) {
                return a.isOpen() ? -1 : 1;
            }

            if( a.mSeekResults != b.mSeekResults &&
                a.mSeekResults != Long.MIN_VALUE &&
                b.mSeekResults != Long.MIN_VALUE )
            {
                return a.mSeekResults < b.mSeekResults ? -1 : 1;
            }

            int diff = typePref( a.mFormat.mType ) - typePref( b.mFormat.mType );
            if( diff != 0 ) {
                return diff;
            }

            return a.mRawStartMicros <= b.mRawStartMicros ? -1 : 1;
        }

        private int typePref( int type ) {
            return type == Jav.AVMEDIA_TYPE_VIDEO ? 1 : 2;
        }
    };

}
