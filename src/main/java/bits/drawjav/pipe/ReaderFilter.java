package bits.drawjav.pipe;

import bits.collect.RingList;
import bits.drawjav.*;
import bits.microtime.*;
import bits.util.ref.Refable;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

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



    public int inputNum() {
        return 0;
    }


    public InPad input( int idx ) {
        return null;
    }


    public int outputNum() {
        return mActive.size();
    }


    public OutPad output( int idx ) {
        return mActive.get( idx );
    }



    public void open( EventBus bus ) {
        if( bus != null ) {
            bus.register( this );
        }
    }


    public boolean isOpen() {
        return mReader.isOpen();
    }


    public void close() {
        try {
            mReader.close();
        } catch( IOException e ) {
            mException = e;
        }
        doClear();
    }


    public void clear() {}



    @Subscribe
    public void processClockEvent( ClockEvent event ) {
        event.apply( this );
    }

    @Override
    public void clockStart( long execMicros ) {}

    @Override
    public void clockStop( long execMicros ) {}

    @Override
    public void clockSeek( long execMicros, long seekMicros ) {
        mException  = null;
        mNeedSeek   = true;
        mSeekMicros = seekMicros;
    }

    @Override
    public void clockRate( long execMicros, Frac rate ) {}


    private void doClear() {
        for( StreamSource source : mActive ) {
            source.clear();
        }
    }


    private final class StreamSource extends OutPadAdapter {
        StreamHandle mStream;
        Queue mQueue = new RingList( MAX_QUEUE_SIZE );

        StreamSource( StreamHandle stream ) {
            mStream = stream;
        }

        @Override
        public int status() {
            return mException == null ? OKAY : EXCEPTION;
        }

        @Override
        public int poll( Refable[] out ) {
            try {
                if( mException != null ) {
                    return EXCEPTION;
                }

                if( mNeedSeek ) {
                    mNeedSeek = false;
                    doClear();
                    mReader.seek( mSeekMicros );
                    return UNFINISHED;
                }

                if( !mQueue.isEmpty() ) {
                    out[0] = (Packet)mQueue.remove();
                    return OKAY;
                }

                Packet packet = mReader.readNext();
                if( packet == null ) {
                    return UNFINISHED;
                }

                StreamSource dest = mMap.get( packet.stream() );
                if( dest == null ) {
                    packet.deref();
                    return UNFINISHED;
                }

                if( dest == this ) {
                    out[0] = packet;
                    return OKAY;
                }

                dest.mQueue.offer( packet );
                return UNFINISHED;

            } catch( IOException e ) {
                mException = e;
                return EXCEPTION;
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