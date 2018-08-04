package science.mengxin.java.btc.scroogecoin;

import java.util.HashSet;

public class TxHandler2 {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current science.mengxin.java.btc.scroogecoin.UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the science.mengxin.java.btc.scroogecoin.UTXOPool(science.mengxin.java.btc.scroogecoin.UTXOPool uPool)
     * constructor.
     */
    public TxHandler2(UTXOPool utxoPool) {
        // IMPLEMENT THIS
        this.utxoPool = utxoPool;
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current science.mengxin.java.btc.scroogecoin.UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no science.mengxin.java.btc.scroogecoin.UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        // IMPLEMENT THIS
        UTXOPool utxoSet = new UTXOPool();
        double pSum = 0;
        double sum = 0;

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input in = tx.getInput(i);
            UTXO u = new UTXO(in.prevTxHash, in.outputIndex);
            Transaction.Output out = utxoPool.getTxOutput(u);

            if ((!utxoPool.contains(u)) || (!Crypto.verifySignature(out.address, tx.getRawDataToSign(i), in.signature)) || (utxoSet.contains(u))) {
                return false;
            }

            utxoSet.addUTXO(u, out);
            pSum += out.value;
        }

        for (Transaction.Output out : tx.getOutputs()) {
            if (out.value < 0) {
                return false;
            }
            sum += out.value;
        }

        if (pSum < sum) {
            return false;
        }

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current science.mengxin.java.btc.scroogecoin.UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // IMPLEMENT THIS
        HashSet<Transaction> txVis = new HashSet<>();
        //fixed point algorithm,iter untill no new transaction is valid
        while (true) {
            boolean updated = false;
            for(Transaction tx: possibleTxs){
                if(txVis.contains(tx))continue;
                if(isValidTx(tx)){
                    txVis.add(tx);
                    updated = true;
                    //add unspent coin
                    for(int i=0 ; i<tx.numOutputs() ; ++i){
                        UTXO utxo = new UTXO(tx.getHash(),i);
                        utxoPool.addUTXO(utxo, tx.getOutput(i));
                    }
                    //delete spent coin
                    for(int i=0 ; i<tx.numInputs() ; ++i){
                        Transaction.Input input = tx.getInput(i);
                        UTXO utxo = new UTXO(input.prevTxHash,input.outputIndex);
                        utxoPool.removeUTXO(utxo);
                    }
                }
            }
            if(!updated)break;
        };
        Transaction[] ret = new Transaction[txVis.size()];
        int idx =0;
        for(Transaction tx : txVis)
            ret[idx++] = tx;
        return ret;
    }

}
