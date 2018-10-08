package com.alibaba.alink.common.matrix;

import org.apache.commons.lang.StringUtils;

import java.io.Serializable;
import java.util.Arrays;

/**
 * We use the concept of 'tensor' to uniform:
 * 1. vector (1-dim tensor)
 * 2. matrix (2-dim tensor)
 * 3. higher ordered tensors
 * <p>
 * All of them can be in dense or sparse form.
 * <p>
 * Some examples:
 * -# vectors
 * [1, 2, 0, 3, 0] is serialized as "1,2,0,3,0" in the dense case, "0:1,1:2,3:3" in the sparse case.
 * <p>
 * -# matrix
 * [[1, 0, 3],[0, 0, 6]] is serialized as "$2,3$1,0,3,0,0,6" in the dense case,
 * "0:0:1,0:2:3,1:2:6" or "$2,3$0:0:1,0:2:3,1:2:6" in the sparse case.
 */
public class Tensor implements Serializable {
    private int[] shapes = null; // shape[i] == -1 indicates that size at i-th dim is unknown
    private int[][] indices = null;
    private double[] data = null;
    private boolean isSparse;

    private Tensor() {
        // an empty tensor is considered a sparse vector with all zeros
        this.isSparse = true;
        this.data = new double[0];
        this.shapes = new int[]{-1};
    }

    private Tensor(int[] shapes) {
        // an empty tensor with shapes is considered a sparse tensor with all zeros
        assert (shapes != null);
        this.isSparse = true;
        this.shapes = shapes;
        this.data = new double[0];
    }

    public Tensor(int[] shapes, double[] data) {
        assert (shapes != null);
        this.isSparse = false;
        this.shapes = shapes;
        this.data = data;

        // infer the size of leading dimension if not given
        int stride = 1;
        for (int i = 1; i < shapes.length; i++) {
            if (shapes[i] < 0)
                throw new IllegalArgumentException("invalid shapes");
            stride *= shapes[i];
        }

        if (shapes.length >= 1)
            shapes[0] = data.length / stride;
    }

    public Tensor(int[] shapes, int indices[][], double[] data) {
        assert (shapes != null);
        this.isSparse = true;
        this.shapes = shapes;
        this.indices = indices;
        this.data = data;
    }

    public Tensor expandDim(int axis) {
        if (isSparse)
            throw new IllegalArgumentException("expand dim for sparse tensor not implemented.");

        int ndim = this.shapes.length;
        if (axis > ndim || axis < -1 - ndim)
            throw new IllegalArgumentException("invalid axis: " + axis);

        if (axis < 0) {
            axis = ndim + 1 + axis;
        }

        int[] newShapes = new int[ndim + 1];

        int i = 0;
        for (; i < axis; i++)
            newShapes[i] = this.shapes[i];
        newShapes[i] = 1;
        for (; i < ndim; i++)
            newShapes[i + 1] = this.shapes[i];

        this.shapes = newShapes;
        return this;
    }

    public Tensor reshape(int[] newshapes) {
        if (isSparse) {
            int[] stride = new int[shapes.length];
            stride[stride.length - 1] = 1;
            for (int i = 0; i < stride.length - 1; i++) {
                stride[stride.length - 1 - 1 - i] = stride[stride.length - 1 - i] * shapes[shapes.length - 1 - i];
            }

            int[] newstride = new int[newshapes.length];
            newstride[newstride.length - 1] = 1;
            for (int i = 0; i < newstride.length - 1; i++) {
                newstride[newstride.length - 1 - 1 - i] = newstride[newstride.length - 1 - i] * newshapes[newshapes.length - 1 - i];
            }

            int[][] newIndices = new int[indices.length][newshapes.length];

            for (int i = 0; i < indices.length; i++) {
                int pos = 0;
                for (int j = 0; j < indices[i].length; j++) {
                    pos += indices[i][j] * stride[j];
                }

                for (int j = 0; j < newIndices[i].length; j++) {
                    newIndices[i][j] = pos / newstride[j];
                    pos = pos % newstride[j];
                }
            }

            this.indices = newIndices;
            this.shapes = newshapes;

        } else {
            this.shapes = newshapes;
        }
        return this;
    }

    public int[] getShapes() {
        return shapes;
    }

    public double[] getData() {
        return data;
    }

    public int[][] getIndices() {
        return indices;
    }

    public boolean isSparse() {
        return isSparse;
    }

    public Tensor standard(double[] mean, double[] stdvar) {
        assert (mean.length == stdvar.length);

        if (isSparse) {
            for (int i = 0; i < indices.length; i++) {
                int which = mean.length == 1 ? 0 : indices[i][0];
                data[i] -= mean[which];
                data[i] *= (1.0 / stdvar[which]);
            }
        } else {
            int size = data.length;
            int stride = size / mean.length;

            for (int i = 0; i < size; i++) {
                int which = i / stride;
                data[i] -= mean[which];
                data[i] *= (1.0 / stdvar[which]);
            }
        }
        return this;
    }

    public Tensor normalize(double[] min, double[] max) {
        assert (min.length == max.length);

        if (isSparse) {
            for (int i = 0; i < indices.length; i++) {
                int which = min.length == 1 ? 0 : indices[i][0];
                data[i] -= min[which];
                data[i] *= 1.0 / (max[which] - min[which]);
            }
        } else {
            int size = data.length;
            int stride = size / min.length;

            for (int i = 0; i < size; i++) {
                int which = i / stride;
                data[i] -= min[which];
                data[i] *= 1.0 / (max[which] - min[which]);
            }
        }
        return this;
    }

    public Tensor toDense() {
        if (isSparse) {
            for (int i = 0; i < shapes.length; i++) {
                if (shapes[i] == -1)
                    throw new RuntimeException("can't convert to dense tensor because shapes is unknown");
            }

            int size = 1;
            for (int i = 0; i < shapes.length; i++) {
                size *= shapes[i];
            }

            int[] stride = new int[shapes.length];
            stride[stride.length - 1] = 1;
            for (int i = 0; i < stride.length - 1; i++) {
                stride[stride.length - 1 - 1 - i] = stride[stride.length - 1 - i] * shapes[shapes.length - 1 - i];
            }

            double[] newdata = new double[size];
            Arrays.fill(newdata, 0.);
            for (int i = 0; i < indices.length; i++) {
                int pos = 0;
                for (int j = 0; j < indices[i].length; j++) {
                    pos += indices[i][j] * stride[j];
                }
                newdata[pos] = data[i];
            }
            data = newdata;
            indices = null;
            isSparse = false;
            return this;
        } else {
            return this;
        }
    }

    public static Tensor parse(String str) {
        try {
            str = StringUtils.trim(str);

            if (str.isEmpty())
                return new Tensor();

            int[] shapes = null;
            if (str.charAt(0) == '$') {
                int lastPos = StringUtils.lastIndexOf(str, '$');
                String shapeInfo = StringUtils.substring(str, 1, lastPos);
                String[] shapesStr = StringUtils.split(shapeInfo, ',');
                shapes = new int[shapesStr.length];
                for (int i = 0; i < shapes.length; i++) {
                    shapes[i] = Integer.valueOf(shapesStr[i].trim());
                }
                str = StringUtils.substring(str, lastPos + 1);
                str = StringUtils.trim(str);
            }

            if (str.isEmpty())
                return new Tensor(shapes);

            String[] valuesStr = StringUtils.split(str, ',');

            // check dense or sparse
            boolean isSparse = (StringUtils.indexOf(valuesStr[0], ':') != -1);

            if (isSparse) {
                int ndim = -1;
                if (null != shapes)
                    ndim = shapes.length;
                double[] data = new double[valuesStr.length];
                int[][] indices = null;
                for (int i = 0; i < valuesStr.length; i++) {
                    if (ndim == -1) {
                        ndim = 0;
                        for (int j = 0; j < valuesStr[i].length(); j++) {
                            if (valuesStr[i].charAt(j) == ':')
                                ndim++;
                        }
                    }
                    if (indices == null) {
                        indices = new int[valuesStr.length][ndim];
                    }
                    if (shapes == null) {
                        shapes = new int[ndim];
                        Arrays.fill(shapes, -1);
                    }
                    String[] kvStr = StringUtils.split(valuesStr[i], ':');
                    if (kvStr.length != ndim + 1)
                        throw new IllegalArgumentException("mismatched size of tensor");
                    for (int j = 0; j < kvStr.length - 1; j++) {
                        indices[i][j] = Integer.valueOf(kvStr[j].trim());
                    }
                    data[i] = Double.valueOf(kvStr[ndim].trim());
                }
                return new Tensor(shapes, indices, data);
            } else {
                if (shapes == null)
                    shapes = new int[]{valuesStr.length};
                double[] data = new double[valuesStr.length];
                for (int i = 0; i < data.length; i++) {
                    data[i] = Double.valueOf(valuesStr[i]);
                }
                return new Tensor(shapes, data);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("fail to parse tensor \"" + str + "\"");
        }
    }

    public String serialize() {
        boolean withShape = false;

        if (shapes != null) {
            if (isSparse || shapes.length > 1) {
                for (int i = 0; i < shapes.length; i++) {
                    if (shapes[i] != -1)
                        withShape = true;
                }
            }
        }

        StringBuilder sbd = new StringBuilder();

        if (withShape) {
            sbd.append("$");
            for (int i = 0; i < shapes.length; i++) {
                sbd.append(shapes[i]);
                if (i < shapes.length - 1)
                    sbd.append(",");
            }
            sbd.append("$");
        }

        if (isSparse) {
            if (null != indices) {
                assert (indices.length == data.length);
                for (int i = 0; i < indices.length; i++) {
                    for (int j = 0; j < indices[i].length; j++) {
                        sbd.append(indices[i][j] + ":");
                    }
                    sbd.append(data[i]);
                    if (i < indices.length - 1)
                        sbd.append(",");
                }
            }
        } else {
            for (int i = 0; i < data.length; i++) {
                sbd.append(data[i]);
                if (i < data.length - 1)
                    sbd.append(",");
            }
        }

        return sbd.toString();
    }

    public DenseVector toDenseVector() {
        int dim = shapes.length;
        if (dim != 1)
            throw new RuntimeException("the data can't be converted to a vector because of dimension error");

        if (isSparse) {
            if (shapes[0] < 0)
                throw new RuntimeException("the data can't be converted to a dense vector because the " +
                        "data is in sparse format and its' size is not specified");
            return toSparseVector().toDenseVector();
        } else {
            return new DenseVector(data);
        }
    }

    public SparseVector toSparseVector() {
        if (isSparse) {
            int dim = shapes.length;
            if (dim != 1)
                throw new RuntimeException("the data can't be converted to sparse vector");
            if (null == indices) {
                return new SparseVector(shapes[0]);
            } else {
                int[] idx = new int[indices.length];
                for (int i = 0; i < idx.length; i++) {
                    idx[i] = indices[i][0];
                }
                return new SparseVector(shapes[0], idx, data);
            }
        } else {
            int idx[] = new int[data.length];
            for (int i = 0; i < data.length; i++) {
                idx[i] = i;
            }
            return new SparseVector(idx.length, idx, data);
        }
    }

    public DenseMatrix toDenseMatrix() {
        int dim = shapes.length;
        if (dim != 2)
            throw new RuntimeException("the data can't be converted to dense matrix because of dimension error");
        if (isSparse)
            throw new RuntimeException("can't convert a sparse tensor to dense matrix");
        return new DenseMatrix(data, shapes[0]);
    }
}
