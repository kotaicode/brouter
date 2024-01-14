/**
 * Filter to eliminate some transfer nodes (Douglas-Peucker algorithm)
 *
 * @author ab
 */
package btools.mapcreator;

import java.util.List;

import btools.util.CheapRuler;

public class DPFilter {
  private static double dp_sql_threshold = 0.4 * 0.4;

  /*
   * for each node (except first+last), eventually set the DP_SURVIVOR_BIT
   */
  public static void doDPFilter(List<OsmNodeP> nodes) {
    int first = 0;
    int last = nodes.size() - 1;
    while (first < last && nodes.get(first + 1).hasBits(OsmNodeP.DP_SURVIVOR_BIT)) {
      first++;
    }
    while (first < last && nodes.get(last - 1).hasBits(OsmNodeP.DP_SURVIVOR_BIT)) {
      last--;
    }
    if (last - first > 1) {
      doDPFilter(nodes, first, last);
    }
  }


  public static void doDPFilter(List<OsmNodeP> nodes, int first, int last) {
    double maxSqDist = -1.;
    int index = -1;
    OsmNodeP p1 = nodes.get(first);
    OsmNodeP p2 = nodes.get(last);

    double[] lonlat2m = CheapRuler.getLonLatToMeterScales((p1.iLat + p2.iLat) >> 1);
    double dlon2m = lonlat2m[0];
    double dlat2m = lonlat2m[1];
    double dx = (p2.iLon - p1.iLon) * dlon2m;
    double dy = (p2.iLat - p1.iLat) * dlat2m;
    double d2 = dx * dx + dy * dy;
    for (int i = first + 1; i < last; i++) {
      OsmNodeP p = nodes.get(i);
      double t = 0.;
      if (d2 != 0f) {
        t = ((p.iLon - p1.iLon) * dlon2m * dx + (p.iLat - p1.iLat) * dlat2m * dy) / d2;
        t = t > 1. ? 1. : (t < 0. ? 0. : t);
      }
      double dx2 = (p.iLon - (p1.iLon + t * (p2.iLon - p1.iLon))) * dlon2m;
      double dy2 = (p.iLat - (p1.iLat + t * (p2.iLat - p1.iLat))) * dlat2m;
      double sqDist = dx2 * dx2 + dy2 * dy2;
      if (sqDist > maxSqDist) {
        index = i;
        maxSqDist = sqDist;
      }
    }
    if (index >= 0) {
      if (index - first > 1) {
        doDPFilter(nodes, first, index);
      }
      if (maxSqDist >= dp_sql_threshold) {
        nodes.get(index).setBits(OsmNodeP.DP_SURVIVOR_BIT);
      }
      if (last - index > 1) {
        doDPFilter(nodes, index, last);
      }
    }
  }
}
