public class TxHandler {
    UTXOPool pool;

    public UTXOPool getPool() {
        return pool;
    }

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        pool = utxoPool;
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        HashSet<UTXO> utxoSet = new HashSet<>();
        double sumOfInputVals = 0, sumOfOutputVals = 0;
        int i = 0;
        // traverse all the input in the transaction,
        for (Transaction.Input input : tx.getInputs()) {
            // create unspent transaction output based on input information
            UTXO lastUTXO = new UTXO(input.prevTxHash, input.outputIndex);
            // check 1 - all output claimed by tx are in current utxopool
            if (!pool.contains(lastUTXO)) {
                return false;
            }
            // pool contain the utxo, get the output
            Transaction.Output prevTx = pool.getTxOutput(lastUTXO);
            // check 2 - signatures of each input are valid
            if (input.signature == null || !Crypto.verifySignature(prevTx.address, tx.getRawDataToSign(i), input.signature)) {
                return false;
            }
            // signature verify successfully, add it to spend set.
            utxoSet.add(lastUTXO);
            // statistic the sum of input value which will be used to check the balance of input and output
            sumOfInputVals += prevTx.value;
            i++;
        }

        // check 3 - no utxo is claimed multiple times  ?
        if (utxoSet.size() != tx.getInputs().size())
            return false;

        // check 4 - non negative output values
        for (Transaction.Output output : tx.getOutputs()) {
            sumOfOutputVals += output.value;
            if (output.value < 0)
                return false;
        }

        // check 5 - validating input values >= sum of output values
        if (sumOfInputVals < sumOfOutputVals)
            return false;

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        UTXOPool tempPool = new UTXOPool(pool);

        ArrayList<Transaction> mValidTxns = new ArrayList<>(); // mutually valid txns
        ArrayList<Transaction> ignoredValidTxns = new ArrayList<>();  // independently valid txns
        ArrayList<Transaction> iValidTxns = new ArrayList<>();  // independently valid txns
        ArrayList<Transaction> pendingTxns = new ArrayList<>();  // txns which may depend on other txns in this set

        // filter out transactions that depend on the current set of transactions.. these will be looked at last
        // definition of depends - tx ref is of one that doesn't exist in utxo pool
        // step 1 find out independently valid transactions and possible dependent txns..
        for (Transaction tx : possibleTxs) {
            if (isValidTxV2(tx) == ThreeState.TRUE)
                iValidTxns.add(tx);
            else if (isValidTxV2(tx) == ThreeState.MAYBE)
                pendingTxns.add(tx);
        }

        while (iValidTxns.size() != 0) {
            Transaction tx = iValidTxns.get(0);
            if (checkIfMutuallyValid(new ArrayList<>(mValidTxns), tx, pool)) {
                mValidTxns.add(tx);
                iValidTxns.remove(0);
            } else {
                ignoredValidTxns.add(tx);
                iValidTxns.remove(0);
            }
        }

        for (Transaction txn : mValidTxns) {
            for (Transaction.Input input : txn.getInputs()) { // remove utxos that have been spent
                UTXO lastUTXO = new UTXO(input.prevTxHash, input.outputIndex);
                tempPool.removeUTXO(lastUTXO);
            }
            int idx = 0;
            for (Transaction.Output out : txn.getOutputs()) {
                UTXO utxo = new UTXO(txn.getHash(), idx);
                tempPool.addUTXO(utxo, out);
                idx++;
            }
        }

        pool = tempPool;

        while (pendingTxns.size() > 0 && mValidTxns.size() > 0) { // check new transactions in the new pool.. apply if can
            Transaction[] pendingTx = new Transaction[pendingTxns.size()];
            pendingTx = pendingTxns.toArray(pendingTx);
            Transaction[] txnPendingCorrect = this.handleTxs(pendingTx);

            if (txnPendingCorrect.length == 0) {
                break;
            } else {
                pendingTxns.removeAll(Arrays.asList(txnPendingCorrect));
                mValidTxns.addAll(Arrays.asList(txnPendingCorrect));
            }
        }
        Transaction[] retVal = new Transaction[mValidTxns.size()];
        retVal = mValidTxns.toArray(retVal);

        return retVal;
    }

}
