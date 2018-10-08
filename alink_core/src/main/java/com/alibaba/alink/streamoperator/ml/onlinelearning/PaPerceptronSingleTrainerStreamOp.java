package com.alibaba.alink.streamoperator.ml.onlinelearning;

import com.alibaba.alink.common.AlinkParameter;
import com.alibaba.alink.batchoperator.BatchOperator;
import com.alibaba.alink.common.constants.ParamName;
import com.alibaba.alink.common.ml.onlinelearning.PerceptronOnlineTrainer;


public class PaPerceptronSingleTrainerStreamOp extends SingleOnlineTrainerStreamOp {

    public PaPerceptronSingleTrainerStreamOp(BatchOperator model, String[] featureColNames, String labelColName, String predResultColName) throws Exception {
        super(model, PerceptronOnlineTrainer.class,
                new AlinkParameter()
                        .put(ParamName.featureColNames, featureColNames)
                        .put(ParamName.labelColName, labelColName)
                        .put(ParamName.predResultColName, predResultColName)
        );
    }


}
