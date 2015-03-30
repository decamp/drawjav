package bits.drawjav.pipe;

import com.google.common.eventbus.EventBus;

import java.util.*;


/**
 * @author Philip DeCamp
 */
public class BasicUnit implements AvUnit {

    protected final List<InPad<?>> mIns;
    protected final List<OutPad> mOuts;


    public BasicUnit( InPad<?> optInPad, OutPad optOutPad ) {
        if( optInPad == null ) {
            mIns = Collections.emptyList();
        } else {
            mIns = new ArrayList<InPad<?>>( 1 );
            mIns.add( optInPad );
        }
        if( optInPad == null ) {
            mOuts = Collections.emptyList();
        } else {
            mOuts = new ArrayList<OutPad>( 1 );
            mOuts.add( optOutPad );
        }
    }


    public BasicUnit( List<InPad<?>> optIns, List<OutPad> optOuts ) {
        if( optIns == null ) {
            mIns = Collections.emptyList();
        } else {
            mIns = new ArrayList<InPad<?>>( optIns );
        }

        if( optOuts == null ) {
            mOuts = Collections.emptyList();
        } else {
            mOuts = new ArrayList<OutPad>( optOuts );
        }
    }


    @Override
    public int inputNum() {
        return mIns.size();
    }

    @Override
    public InPad input( int idx ) {
        return (InPad)mIns.get( idx );
    }

    @Override
    public int outputNum() {
        return mOuts.size();
    }

    @Override
    public OutPad output( int idx ) {
        return mOuts.get( idx );
    }

    @Override
    public void open( EventBus bus ) {}

    @Override
    public void close() {}

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void clear() {}

}
