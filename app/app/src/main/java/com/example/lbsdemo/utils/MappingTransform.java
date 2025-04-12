// MappingTransform.java
package com.example.lbsdemo.utils;

import org.apache.commons.math3.linear.*;
import java.util.ArrayList;
import java.util.List;

public class MappingTransform {
    private double[] coefficientsX;
    private double[] coefficientsY;

    /**
     * 构造函数，基于映射点计算多项式变换系数，到了算法时间。。。
     * 以二次多项式为例：
     * x = a0 + a1*lng + a2*lat + a3*lng*lat + a4*lng^2 + a5*lat^2
     * y = b0 + b1*lng + b2*lat + b3*lng*lat + b4*lng^2 + b5*lat^2
     */
    public MappingTransform() {
        List<Point> points = getMappingPoints();
        int N = points.size();
        if (N < 6) { // 二次多项式需要至少6个点，所以我们给了很多点
            throw new IllegalArgumentException("至少需要6个映射点进行二次多项式变换");
        }

        double[][] A = new double[N][6];
        double[] Bx = new double[N];
        double[] By = new double[N];

        for (int i = 0; i < N; i++) {
            Point p = points.get(i);
            A[i][0] = 1;
            A[i][1] = p.lng;
            A[i][2] = p.lat;
            A[i][3] = p.lng * p.lat;
            A[i][4] = p.lng * p.lng;
            A[i][5] = p.lat * p.lat;
            Bx[i] = p.x;
            By[i] = p.y;
        }

        RealMatrix matrixA = new Array2DRowRealMatrix(A);
        RealVector vectorBx = new ArrayRealVector(Bx);
        RealVector vectorBy = new ArrayRealVector(By);

        // 使用最小二乘法求解
        DecompositionSolver solver = new QRDecomposition(matrixA).getSolver();
        RealVector solutionX = solver.solve(vectorBx);
        RealVector solutionY = solver.solve(vectorBy);

        coefficientsX = new double[6];
        coefficientsY = new double[6];
        for (int i = 0; i < 6; i++) {
            coefficientsX[i] = solutionX.getEntry(i);
            coefficientsY[i] = solutionY.getEntry(i);
        }
    }

    /**
     * 定义映射点数据
     * 您可以将此方法改为从文件或数据库加载映射点数据
     * @return 72映射点列表
     */
    private List<Point> getMappingPoints() {
        List<Point> points = new ArrayList<>();

        //
        points.add(new Point(120.74365, 31.281209, 88, 42));
        points.add(new Point(120.745537, 31.281244, 256, 41));
        points.add(new Point(120.745514, 31.281063, 254, 58));
        points.add(new Point(120.745353, 31.280773, 254, 58));
        points.add(new Point(120.745353, 31.280773, 232, 87));
        points.add(new Point(120.744755, 31.280449, 185, 98));
        points.add(new Point(120.744499, 31.280468, 144, 97));
        points.add(new Point(120.743888, 31.280746, 107, 86));
        points.add(new Point(120.743879, 31.280847, 107, 71));
        points.add(new Point(120.743673, 31.28102, 88, 60));
        // 添加更多映射点以覆盖整个地图区域
        points.add(new Point(120.746000, 31.281500, 300, 150));
        // ... 继续添加其他映射点
        points.add(new Point(120.745752, 31.280098, 273, 152));
        points.add(new Point(120.744701, 31.280071, 190, 152));
        points.add(new Point(120.74383, 31.279747, 108, 188));
        points.add(new Point(120.745303, 31.279778, 246, 189));
        points.add(new Point(120.745353, 31.278698, 246, 302));
        points.add(new Point(120.744311, 31.278682, 147, 302));
        points.add(new Point(120.744064, 31.279211, 125, 242));
        // sa
        points.add(new Point(120.745514, 31.279867, 256, 175));
        points.add(new Point(120.745869, 31.279797, 289, 173));
        points.add(new Point(120.746794, 31.279817, 365, 182));
        points.add(new Point(120.745887, 31.279635, 289, 265));
        points.add(new Point(120.746799, 31.279643, 365, 203));
        points.add(new Point(120.746224, 31.279423, 314, 222));
        points.add(new Point(120.745986, 31.279423, 291, 221));
        //sb-SD
        points.add(new Point(120.746736, 31.279357, 361, 231));
        points.add(new Point(120.746817, 31.27935, 366, 232));
        points.add(new Point(120.746826, 31.27918, 366, 253));
        points.add(new Point(120.74674, 31.279168, 361, 254));
        points.add(new Point(120.745905, 31.279172, 288, 253));
        points.add(new Point(120.745896, 31.279346, 289, 232));
        points.add(new Point(120.746013, 31.278895, 293, 279));
        points.add(new Point(120.74683, 31.278906, 366, 279));
        points.add(new Point(120.746844, 31.278729, 366, 301));
        points.add(new Point(120.745923, 31.278713, 288, 300));
        points.add(new Point(120.745937, 31.27842, 290, 327));
        points.add(new Point(120.746857, 31.278435, 365, 237));
        points.add(new Point(120.746871, 31.278246, 366, 348));
        points.add(new Point(120.745945, 31.278227, 288, 346));
        //PB, Downstairs square, EE
        points.add(new Point(120.747028, 31.279781, 395, 184));
        points.add(new Point(120.747841, 31.279804, 462, 182));
        points.add(new Point(120.747041, 31.279585, 397, 205));
        points.add(new Point(120.747872, 31.279318, 462, 230));
        points.add(new Point(120.747872, 31.279318, 423, 231));
        points.add(new Point(120.747149, 31.279291, 296, 231));
        points.add(new Point(120.747149, 31.279291, 397, 317));
        points.add(new Point(120.747401, 31.278624, 420, 309));
        points.add(new Point(120.748021, 31.278995, 466, 275));
        points.add(new Point(120.748048, 31.278466, 466, 329));
        points.add(new Point(120.74807, 31.278262, 466, 348));
        points.add(new Point(120.747199, 31.278435, 395, 328));
        points.add(new Point(120.747203, 31.278254, 396, 348));
        points.add(new Point(120.748003, 31.279812, 471, 184));
        points.add(new Point(120.748587, 31.279828, 528, 184));
        points.add(new Point(120.748003, 31.279616, 472, 205));
        points.add(new Point(120.7486, 31.279627, 528, 205));
        points.add(new Point(120.748286, 31.279427, 503, 223));
        points.add(new Point(120.748551, 31.279438, 523, 222));
        points.add(new Point(120.74829, 31.279427, 503, 221));
        points.add(new Point(120.748034, 31.279361, 476, 227));
        points.add(new Point(120.748955, 31.279388, 559, 227));
        points.add(new Point(120.748039, 31.279187, 475, 246));
        points.add(new Point(120.748964, 31.279207, 558, 246));
        points.add(new Point(120.748228, 31.278937, 489, 273));
        points.add(new Point(120.749171, 31.278952, 575, 274));
        points.add(new Point(120.749198, 31.278289, 675, 344));
        points.add(new Point(120.748272, 31.278262, 488, 343));
        points.add(new Point(120.748246, 31.278431, 490, 326));


        return points;
    }

    /**
     * 将实际经纬度转换为图片像素坐标
     */
    public Point transform(double lng, double lat) {
        double x = coefficientsX[0]
                + coefficientsX[1] * lng
                + coefficientsX[2] * lat
                + coefficientsX[3] * lng * lat
                + coefficientsX[4] * lng * lng
                + coefficientsX[5] * lat * lat;

        double y = coefficientsY[0]
                + coefficientsY[1] * lng
                + coefficientsY[2] * lat
                + coefficientsY[3] * lng * lat
                + coefficientsY[4] * lng * lng
                + coefficientsY[5] * lat * lat;

        return new Point(lng, lat, x, y);
    }

    /**
     * 定义映射点类
     */
    public static class Point {
        public double lng; // 经度
        public double lat; // 纬度
        public double x;   // 图片像素x
        public double y;   // 图片像素y

        public Point(double lng, double lat, double x, double y) {
            this.lng = lng;
            this.lat = lat;
            this.x = x;
            this.y = y;
        }
    }
}
