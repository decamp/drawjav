package bits.drawjav.pipe;

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
public class PacketReaderUnit implements AvUnit, SyncClockControl {

    private static final int MAX_QUEUE_SIZE = 64;

    private final PacketReader mReader;
    private final OutHandler[] mSources;
    private final List<OutHandler>        mActive = new ArrayList<OutHandler>();
    private final Map<Stream, OutHandler> mMap    = new HashMap<Stream, OutHandler>();

    private Exception mException  = null;
    private boolean   mNeedSeek   = false;
    private long      mSeekMicros = 0;


    public PacketReaderUnit( PacketReader reader ) {
        mReader = reader;
        int len = reader.streamCount();
        mSources = new OutHandler[len];
        for( int i = 0; i < len; i++ ) {
            OutHandler source = new OutHandler( reader.stream( i ) );
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
        for( OutHandler source : mActive ) {
            source.clear();
        }
    }


    private final class OutHandler extends OutPadAdapter {
        private Stream mStream;
        private Queue<Refable> mQueue = new ArrayDeque<Refable>( MAX_QUEUE_SIZE );

        OutHandler( Stream stream ) {
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
                    out[0] = mQueue.remove();
                    return OKAY;
                }

                Packet packet = mReader.readNext();
                if( packet == null ) {
                    return UNFINISHED;
                }

                OutHandler dest = mMap.get( packet.stream() );
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
                (mQueue.remove()).deref();
            }
        }

    }

}
