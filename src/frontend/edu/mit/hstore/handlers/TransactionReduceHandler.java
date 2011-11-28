package edu.mit.hstore.handlers;

import java.util.Collection;

import org.apache.log4j.Logger;
import org.voltdb.StoredProcedureInvocation;
import org.voltdb.messaging.FastDeserializer;

import ca.evanjones.protorpc.ProtoRpcController;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;

import edu.brown.hstore.Hstore.HStoreService;
import edu.brown.hstore.Hstore.TransactionRedirectRequest;
import edu.brown.hstore.Hstore.TransactionReduceRequest;
import edu.brown.hstore.Hstore.TransactionReduceResponse;
import edu.brown.logging.LoggerUtil;
import edu.brown.logging.LoggerUtil.LoggerBoolean;
import edu.mit.hstore.HStoreCoordinator;
import edu.mit.hstore.HStoreSite;
import edu.mit.hstore.dtxn.LocalTransaction;
import edu.mit.hstore.dtxn.MapReduceTransaction;

public class TransactionReduceHandler
		extends
		AbstractTransactionHandler<TransactionReduceRequest, TransactionReduceResponse> {
	private static final Logger LOG = Logger
			.getLogger(TransactionReduceHandler.class);
	private static final LoggerBoolean debug = new LoggerBoolean(LOG
			.isDebugEnabled());
	private static final LoggerBoolean trace = new LoggerBoolean(LOG
			.isTraceEnabled());
	static {
		LoggerUtil.attachObserver(LOG, debug, trace);
	}

	public TransactionReduceHandler(HStoreSite hstore_site,
			HStoreCoordinator hstore_coord) {
		super(hstore_site, hstore_coord);
	}

	@Override
	public void sendLocal(long txn_id, TransactionReduceRequest request,
			Collection<Integer> partitions,
			RpcCallback<TransactionReduceResponse> callback) {
		handler.transactionReduce(null, request, callback);
	}

	@Override
	public void sendRemote(HStoreService channel,
			ProtoRpcController controller, TransactionReduceRequest request,
			RpcCallback<TransactionReduceResponse> callback) {
		channel.transactionReduce(controller, request, callback);
	}

	@Override
	public void remoteQueue(RpcController controller,
			TransactionReduceRequest request,
			RpcCallback<TransactionReduceResponse> callback) {
		this.remoteHandler(controller, request, callback);
	}

	@Override
	public void remoteHandler(RpcController controller,
			TransactionReduceRequest request,
			RpcCallback<TransactionReduceResponse> callback) {
		assert (request.hasTransactionId()) : "Got Hstore."
				+ request.getClass().getSimpleName() + " without a txn id!";
		long txn_id = request.getTransactionId();
		if (debug.get())
			LOG.debug("__FILE__:__LINE__ "
					+ String.format("Got %s for txn #%d", request.getClass()
							.getSimpleName(), txn_id));

		// Deserialize the StoredProcedureInvocation object
        StoredProcedureInvocation invocation = null;
        try {
        	invocation = FastDeserializer.deserialize(request.getInvocation().toByteArray(), StoredProcedureInvocation.class);
        } catch (Exception ex) {
        	throw new RuntimeException("Unexpected error when deserializing StoredProcedureInvocation", ex);
        }
        
        MapReduceTransaction ts = hstore_site.createMapReduceTransaction(txn_id, invocation, request.getBasePartition());
        for (int partition : hstore_site.getLocalPartitionIds()) {
            hstore_site.transactionStart(ts, partition);
        }


	}

	@Override
	protected ProtoRpcController getProtoRpcController(LocalTransaction ts,
			int site_id) {
		return ts.getTransactionWorkController(site_id);
	}
}
