package bits.drawjav;

import java.io.*;
import java.util.*;

import bits.drawjav.audio.*;
import bits.drawjav.video.*;
import bits.jav.*;
import static bits.jav.Jav.*;
import bits.jav.codec.*;
import bits.jav.format.*;
import bits.jav.util.Rational;
import bits.util.Guid;


/**
 * Not thread-safe.
 * 
 * @author decamp
 */
public class FormatDecoder implements Source {


    public static FormatDecoder openFile( File file ) throws IOException {
        return openFile( file, false, 0L );
    }


    public static FormatDecoder openFile( File file,
                                          boolean overrideStartMicros,
                                          long startMicros )
                                          throws IOException
    {
        Jav.init();
        if( !file.exists() ) {
            throw new FileNotFoundException();
        }
        JavFormatContext format = JavFormatContext.openInput( file );
        return new FormatDecoder( format, overrideStartMicros, startMicros );
    }


    public static FormatDecoder open( JavFormatContext format ) throws IOException {
        return open( format, false, 0L );
    }


    public static FormatDecoder open( JavFormatContext format,
                                      boolean overrideStartMicros,
                                      long startMicros )
                                      throws IOException
    {
        return new FormatDecoder( format, overrideStartMicros, startMicros );
    }


    private static final Rational MICROS = new Rational( 1000000, 1 );


    private final JavFormatContext mFormat;
    private final JavPacket mPacket;
    private final JavPacket mNullPacket;
    private final Stream[]  mStreams;

    private int    mFirstStreamIndex = -1;
    private Stream mDefaultStream    = null;

    private boolean mPacketValid = false;
    private boolean mEof         = false;
    private int mFlushStream     = -1;


    private FormatDecoder( JavFormatContext format,
                           boolean overrideStartMicros,
                           long startMicros )
    {
        mFormat     = format;
        mPacket     = JavPacket.alloc();
        mNullPacket = JavPacket.alloc();
        mStreams    = new Stream[format.streamCount()];

        long firstMicros       = Long.MAX_VALUE;
        long firstPts          = Long.MAX_VALUE;
        Rational firstTimeBase = null;

        for( int i = 0; i < mStreams.length; i++ ) {
            JavStream s = format.stream( i );
            long pts    = s.startTime();
            Rational tb = s.timeBase();
            long micros = Rational.rescaleQ( pts, MICROS, tb );
            int type    = s.codecContext().codecType();

            if( type == AVMEDIA_TYPE_VIDEO || type == AVMEDIA_TYPE_AUDIO ) {
                if( mFirstStreamIndex < 0 || micros < firstMicros ) {
                    firstPts          = pts;
                    firstTimeBase     = tb;
                    firstMicros       = micros;
                    mFirstStreamIndex = i;
                }
            }
        }

        for( int i = 0; i < mStreams.length; i++ ) {
            JavStream s   = format.stream( i );
            Stream ss     = null;
            long startPts = 0;

            if( overrideStartMicros ) {
                Rational tb = s.timeBase();
                startPts = Rational.rescaleQ( firstPts, firstTimeBase, tb );
            }

            switch( s.codecContext().codecType() ) {
            case AVMEDIA_TYPE_VIDEO:
                ss = new VideoStream( s, startPts, startMicros );
                break;
            case AVMEDIA_TYPE_AUDIO:
                ss = new AudioStream( s, startPts, startMicros );
                break;
            default:
                ss = new NullStream( s );
                break;
            }

            mStreams[i] = ss;
        }

        if( mFirstStreamIndex != -1 ) {
            mDefaultStream = mStreams[mFirstStreamIndex];
        }
    }



    @Override
    public int streamCount() {
        return mStreams.length;
    }

    @Override
    public StreamHandle stream( int index ) {
        return mStreams[index];
    }

    @Override
    public StreamHandle stream( Guid guid ) {
        for( Stream s : mStreams ) {
            if( s.guid().equals( guid ) ) {
                return s;
            }
        }

        return null;
    }


    public StreamHandle stream( int mediaType, int index ) {
        for( int i = 0; i < mStreams.length; i++ ) {
            if( mStreams[i].type() == mediaType ) {
                if( index-- == 0 ) {
                    return mStreams[i];
                }
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings( { "rawtypes", "unchecked" } )
    public List<StreamHandle> streams() {
        return (List)Arrays.asList( mStreams );
    }


    public JavFormatContext formatContext() {
        return mFormat;
    }

    @Override
    public void openStream( StreamHandle stream ) throws JavException {
        openStream( stream, 32 );
    }


    public void openStream( StreamHandle stream, int poolSize ) throws JavException {
        Stream ss = mStreams[((Stream)stream).index()];
        synchronized( this ) {
            if( ss.isOpen() ) {
                return;
            }

            switch( ss.type() ) {
            case AVMEDIA_TYPE_AUDIO:
            {
                AudioStream s = (AudioStream)ss;
                s.open( new OneStreamAudioAllocator( poolSize, -1, -1 ) );
                return;
            }
            case AVMEDIA_TYPE_VIDEO:
            {

                VideoStream s = (VideoStream)ss;
                s.open( new OneStreamVideoAllocator( poolSize, -1 ) );
                return;
            }
            default:
            }
        }
    }


    public void openVideoStream( StreamHandle stream, VideoAllocator alloc ) throws JavException {
        Stream ss = mStreams[((Stream)stream).index()];
        if( ss.type() != Jav.AVMEDIA_TYPE_VIDEO ) {
            throw new IllegalArgumentException( "Not a video stream." );
        }
        ((VideoStream)ss).open( alloc );
    }


    public void openAudioStream( StreamHandle stream, AudioAllocator alloc ) throws JavException {
        Stream ss = mStreams[((Stream)stream).index()];
        if( ss.type() != Jav.AVMEDIA_TYPE_AUDIO ) {
            throw new IllegalArgumentException( "Not an audio stream." );
        }
        ((AudioStream)ss).open( alloc );
    }


    @Override
    public void closeStream( StreamHandle stream ) throws IOException {
        Stream ss = mStreams[((Stream)stream).index()];
        synchronized( this ) {
            if( !ss.isOpen() ) {
                return;
            }
            ss.close();
        }
    }

    @Override
    public void seek( long micros ) throws JavException {
        doSeek( micros, Jav.AVSEEK_FLAG_BACKWARD );
    }

    @Override
    public Packet readNext() throws IOException {
        if( mEof ) {
            return flush();
        }

        // Check if we still have packet.
        if( !mPacketValid ) {
            int err = mFormat.readPacket( mPacket );
            if( err != 0 ) {
                if( err == Jav.AVERROR_EOF ) {
                    mEof = true;
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

    @Override
    public boolean isOpen() {
        return mDefaultStream != null;
    }

    @Override
    public void close() throws IOException {
        for( int i = 0; i < mStreams.length; i++ ) {
            try {
                Stream ss = mStreams[i];
                if( ss == null || !ss.isOpen() ) {
                    continue;
                }
                ss.close();
            } catch( Exception ignored ) {}
        }
    }


    public void overrideTimestamps( long mediaStartMicros ) {
        if( mFirstStreamIndex < 0 ) {
            return;
        }

        JavStream ss       = mFormat.stream( mFirstStreamIndex );
        Rational firstBase = ss.timeBase();
        long firstPts      = ss.startTime();

        for( Stream s : mStreams ) {
            long startPts = Rational.rescaleQ( firstPts, s.timeBase(), firstBase );
            s.initTimer( startPts, mediaStartMicros );
        }
    }


    public void useEmbeddedTimestamps( long offsetMicros ) {
        for( Stream s : mStreams ) {
            s.initTimer( 0, offsetMicros );
        }
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


    private void doSeek( long micros, int flags ) {
        Stream stream = mDefaultStream;
        if( stream == null ) {
            return;
        }

        Rational timeBase = stream.timeBase();
        long pts          = stream.microsToPts( micros );
        long startPts     = stream.javStream().startTime();

        mFormat.seek( stream.index(), pts, flags );

        for( int i = 0; i < mStreams.length; i++ ) {
            Stream ss = mStreams[i];
            if( timeBase.equals( ss.timeBase() ) ) {
                ss.seekPts( pts - startPts + ss.javStream().startTime() );
            } else {
                ss.seekMicros( micros );
            }
        }

        mEof         = false;
        mPacketValid = false;
        mFlushStream = 0;
    }



    private static abstract class Stream implements StreamHandle {

        final JavStream mStream;
        final Guid mGuid;
        final int mIndex;
        final int mType;
        final Rational mTimeBase;
        final PacketTimer mTimer;

        Stream( JavStream stream ) {
            mStream = stream;
            mGuid = Guid.create();
            mIndex = stream.index();
            mType = stream.codecContext().codecType();
            mTimeBase = stream.timeBase();
            mTimer = new PacketTimer( mTimeBase );
        }


        @Override
        public Guid guid() {
            return mGuid;
        }


        @Override
        public int type() {
            return mType;
        }


        @Override
        public PictureFormat pictureFormat() {
            return null;
        }


        @Override
        public AudioFormat audioFormat() {
            return null;
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

        public abstract boolean isOpen();

        public abstract void close() throws IOException;

        abstract Packet process( JavPacket packet, boolean flushing ) throws IOException;

        abstract void seekPts( long pts );

        abstract void seekMicros( long micros );

    }


    private static class VideoStream extends Stream {

        private final JavCodecContext mCodecContext;
        private final PictureFormat   mFormat;

        private final long[] mRange    = new long[2];
        private final int[]  mGotFrame = new int[1];

        private boolean        mIsOpen       = false;
        private VideoAllocator mAlloc        = null;
        private boolean        mHasKeyFrame  = false;
        private VideoPacket    mCurrentFrame = null;


        public VideoStream( JavStream stream,
                            long startPts,
                            long startMicros )
        {
            super( stream );
            mCodecContext = stream.codecContext();
            mFormat = PictureFormat.fromCodecContext( mCodecContext );
            initTimer( startPts, startMicros );
        }


        @Override
        public PictureFormat pictureFormat() {
            return mFormat;
        }


        public void open( VideoAllocator alloc ) throws JavException {
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

            VideoPacket ret = mCurrentFrame;
            mCurrentFrame = null;
            if( ret == null ) {
                ret = mAlloc.alloc( mFormat );
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
            ret.init( this, mFormat, mRange[0], mRange[1] );

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


    private static class AudioStream extends Stream {

        private final JavCodecContext mCodecContext;
        private final AudioFormat     mFormat;

        private final long[] mRange    = new long[2];
        private final int[]  mGotFrame = new int[1];

        private boolean        mIsOpen       = false;
        private AudioAllocator mAlloc        = null;
        private AudioPacket    mCurrentFrame = null;


        public AudioStream( JavStream stream,
                            long startPts,
                            long startMicros )
        {
            super( stream );
            mCodecContext = stream.codecContext();
            mFormat = AudioFormat.fromCodecContext( mCodecContext );
            initTimer( startPts, startMicros );
        }


        public void open( AudioAllocator alloc ) throws JavException {
            if( mIsOpen ) {
                return;
            }
            if( !mCodecContext.isOpen() ) {
                JavCodec codec = JavCodec.findDecoder( mCodecContext.codecId() );
                if( codec == null ) {
                    throw new JavException( AVERROR_DECODER_NOT_FOUND,
                                            "Codec not found for stream: " + mStream.index() );
                }
                mCodecContext.open( codec );
            }

            mAlloc = alloc;
            alloc.ref();
            mIsOpen = true;
        }

        @Override
        public AudioFormat audioFormat() {
            return mFormat;
        }

        @Override
        public boolean isOpen() {
            return mIsOpen;
        }

        @Override
        public void close() {
            if( mAlloc != null ) {
                mAlloc.deref();
                mAlloc = null;
            }
            if( mCodecContext.isOpen() ) {
                try {
                    mCodecContext.close();
                } catch( Exception ignore ) {}
            }
        }

        @Override
        public AudioPacket process( JavPacket packet, boolean flushing ) throws IOException {
            if( mAlloc == null ) {
                if( !flushing ) {
                    mTimer.packetSkipped( packet.pts(), packet.duration(), mRange );
                    packet.moveDataPointer( packet.size() );
                }
                return null;
            }

            AudioPacket ret = mCurrentFrame;
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
            ret.init( this, mFormat, mRange[0], mRange[1] );
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


    private static class NullStream extends Stream {

        private final long[] mRange = new long[2];


        public NullStream( JavStream stream ) {
            super( stream );
        }


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

}
