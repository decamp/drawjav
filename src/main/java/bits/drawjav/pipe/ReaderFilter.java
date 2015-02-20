package bits.drawjav.pipe;

import bits.collect.RingList;
import bits.drawjav.*;
import bits.microtime.Frac;
import bits.microtime.SyncClockControl;
import bits.util.ref.Refable;

import java.io.*;
import java.util.*;


/**
 * @author Philip DeCamp
 */
public class ReaderFilter implements Filter, SyncClockControl {

    private static final int MAX_QUEUE_SIZE = 64;

    private final PacketReader   mReader;
    private final StreamSource[] mSources;
    private final List<StreamSource>              mActive = new ArrayList<StreamSource>();
    private final Map<StreamHandle, StreamSource> mMap    = new HashMap<StreamHandle, StreamSource>();

    private FilterErr mState      = FilterErr.NONE;
    private Exception mException  = null;
    private boolean   mNeedSeek   = false;
    private long      mSeekMicros = 0;


    public ReaderFilter( PacketReader reader ) {
        mReader = reader;
        int len = reader.streamCount();
        mSources = new StreamSource[len];
        for( int i = 0; i < len; i++ ) {
            StreamSource source = new StreamSource( reader.stream( i ) );
            if( reader.isStreamOpen( source.mStream ) ) {
                mActive.add( source );
                mMap.put( source.mStream, source );
            }
        }
    }



    public int sinkNum() {
        return 0;
    }


    public SinkPad sink( int idx ) {
        return null;
    }


    public int sourceNum() {
        return mActive.size();
    }


    public SourcePad source( int idx ) {
        return mActive.get( idx );
    }


    public void clear() {}


    public boolean isOpen() {
        return mReader.isOpen();
    }


    public void close() throws IOException {
        mReader.close();
        doClear();
    }



    @Override
    public void clockStart( long execMicros ) {}

    @Override
    public void clockStop( long execMicros ) {}

    @Override
    public void clockSeek( long execMicros, long seekMicros ) {
        mState = FilterErr.NONE;
        mNeedSeek = true;
        mSeekMicros = seekMicros;
    }

    @Override
    public void clockRate( long execMicros, Frac rate ) {}


    private void doClear() {
        for( StreamSource source : mActive ) {
            source.clear();
        }
    }


    private final class StreamSource implements SourcePad {
        StreamHandle mStream;
        Queue mQueue = new RingList( MAX_QUEUE_SIZE );
        Exception mException = null;

        StreamSource( StreamHandle stream ) {
            mStream = stream;
        }


        public boolean blocks() {
            return true;
        }

        public int available() {
            return mQueue.size();
        }

        public FilterErr remove( Packet[] out, long blockMicros ) {
            try {
                if( mNeedSeek ) {
                    mNeedSeek = false;
                    doClear();
                    mReader.seek( mSeekMicros );
                    return FilterErr.NONE;
                }

                if( !mQueue.isEmpty() ) {
                    out[0] = (Packet)mQueue.remove();
                    return FilterErr.DONE;
                }

                if( mState != FilterErr.NONE ) {
                    return mState;
                }

                Packet packet = mReader.readNext();
                if( packet == null ) {
                    return FilterErr.NONE;
                }

                StreamSource dest = mMap.get( packet.stream() );
                if( dest == null ) {
                    packet.deref();
                    return FilterErr.NONE;
                }

                if( dest == this ) {
                    out[0] = packet;
                    return FilterErr.DONE;
                }

                dest.mQueue.offer( packet );
                return FilterErr.NONE;

            } catch( EOFException ex ) {
                mState = FilterErr.UNDERFLOW;
                return mState;

            } catch( IOException e ) {
                mState = FilterErr.EXCEPTION;
                mException = e;
                return mState;
            }
        }

        public Exception exception() {
            return mException;
        }


        private void clear() {
            while( !mQueue.isEmpty() ) {
                ((Refable)mQueue.remove()).deref();
            }
        }

    }

}
