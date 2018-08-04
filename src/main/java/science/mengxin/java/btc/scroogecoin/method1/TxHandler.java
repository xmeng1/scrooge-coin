package science.mengxin.java.btc.scroogecoin.method1;

import science.mengxin.java.btc.scroogecoin.Crypto;
import science.mengxin.java.btc.scroogecoin.Transaction;
import science.mengxin.java.btc.scroogecoin.UTXO;
import science.mengxin.java.btc.scroogecoin.UTXOPool;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

public class TxHandler {
    UTXOPool pool;

    public UTXOPool getPool() {
        return pool;
    }


    /**
     * Creates a public ledger whose current ScroogeCore.UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the ScroogeCore.UTXOPool(ScroogeCore.UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        pool = utxoPool;
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current ScroogeCore.UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no ScroogeCore.UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     * values; and false otherwise.
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
     * updating the current ScroogeCore.UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
// IMPLEMENT THIS
        HashSet<Transaction>txVis = new HashSet<>();
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
                        pool.addUTXO(utxo, tx.getOutput(i));
                    }
                    //delete spent coin
                    for(int i=0 ; i<tx.numInputs() ; ++i){
                        Transaction.Input input = tx.getInput(i);
                        UTXO utxo = new UTXO(input.prevTxHash,input.outputIndex);
                        pool.removeUTXO(utxo);
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