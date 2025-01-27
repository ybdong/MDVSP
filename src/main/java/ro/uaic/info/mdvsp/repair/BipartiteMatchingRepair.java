/*
 * Copyright (C) 2020 Cristian Frasinaru
 */
package ro.uaic.info.mdvsp.repair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jgrapht.alg.interfaces.MatchingAlgorithm;
import org.jgrapht.alg.matching.KuhnMunkresMinimalWeightBipartitePerfectMatching;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import ro.uaic.info.mdvsp.Model;
import ro.uaic.info.mdvsp.Solution;
import ro.uaic.info.mdvsp.Tour;

/**
 *
 * @author Cristian Frăsinaru
 */
public class BipartiteMatchingRepair {

    private final Model model;
    private final int m;
    private final int n;
    private final int cost[][];
    private final int repairType; //1 or 2

    public BipartiteMatchingRepair(Model model, int repairType) {
        this.model = model;
        this.m = model.nbDepots();
        this.n = model.nbTrips();
        this.cost = model.getCost();
        this.repairType = repairType;
    }

    /**
     * Self repair, two variants.
     *
     * @param t
     * @return
     */
    private Repair repair(Tour t) {
        if (repairType == 1) {
            int i = t.size() - 2;
            Repair ra = new RepairImpl1a(t, cost[t.get(i)][t.get(0)] - cost[t.get(i)][t.get(i + 1)]);
            return ra;
        } else {
            int j = t.size() - 1;
            Repair rb = new RepairImpl1b(t, cost[t.get(j)][t.get(1)] - cost[t.get(0)][t.get(1)]);
            return rb;
        }
    }

    /**
     *
     * @param t1
     * @param t2
     * @return
     */
    private Repair repair(Tour t1, Tour t2) {
        if (Math.abs(t1.getId()) == Math.abs(t2.getId())) {
            //self repair
            return repair(t1);
        }
        if (t1.last() != t2.first() || t2.last() != t1.first()) {
            //incompatible tours
            return new RepairImpl2(t1, t2, -1, -1, Integer.MAX_VALUE);
        }
        int min = Integer.MAX_VALUE;
        int pos1 = -1;
        int pos2 = -1;
        for (int i = 1, n1 = t1.size(); i < n1 - 2; i++) {
            for (int j = 2, n2 = t2.size(); j < n2 - 1; j++) {
                int i1 = t1.get(i);
                int i2 = t1.get(i + 1);
                int j1 = t2.get(j - 1);
                int j2 = t2.get(j);
                if (cost[i1][j2] == -1 || cost[j1][i2] == -1) {
                    continue;
                }
                int repair = cost[i1][j2] - cost[i1][i2] + cost[j1][i2] - cost[j1][j2];
                if (min > repair) {
                    min = repair;
                    pos1 = i;
                    pos2 = j;
                }
            }
        }
        //compatible tours repair
        return new RepairImpl2(t1, t2, pos1, pos2, min);
    }

    /**
     *
     * @return
     */
    Solution repair(Solution sol) {
        List<Tour> tours = sol.getTours();
        List<Tour> badTours = tours.stream().filter(t -> t.isBad()).collect(Collectors.toList());
        //List<Tour> badTours = new ArrayList<>(tours);
        
        Map<Tour, Tour> map = new HashMap<>();
        Set<Tour> part1 = new HashSet<>();
        Set<Tour> part2 = new HashSet<>();
        SimpleWeightedGraph<Tour, DefaultWeightedEdge> bip
                = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

        //add nodes to the bipartite graph
        int k = badTours.size();
        for (int i = 0; i < k; i++) {
            Tour t1 = badTours.get(i);
            t1.setId(i + 1);
            Tour t2 = new Tour(badTours.get(i), -t1.getId());
            part1.add(t1);
            part2.add(t2);
            bip.addVertex(t1);
            bip.addVertex(t2);
            map.put(t1, t2);
            map.put(t2, t1);
        }

        //add edges
        for (int i = 0; i < k; i++) {
            Tour t1 = badTours.get(i);
            for (int j = 0; j < k; j++) {
                Tour t2 = badTours.get(j);
                Repair repair = repair(t1, t2);
                DefaultWeightedEdge e = bip.addEdge(t1, map.get(t2));
                bip.setEdgeWeight(e, repair.getValue());
            }
        }

        //apply the algorithm: minimum cost perfect matching
        List<Tour> repairedTours = tours.stream().filter(t -> !t.isBad()).collect(Collectors.toList());
        //List<Tour> repairedTours = new ArrayList<>();
        KuhnMunkresMinimalWeightBipartitePerfectMatching alg
                = new KuhnMunkresMinimalWeightBipartitePerfectMatching(bip, part1, part2);
        MatchingAlgorithm.Matching match = alg.getMatching();
        int q = 0;
        for (Object obj : match.getEdges()) {
            DefaultWeightedEdge e = (DefaultWeightedEdge) obj;
            //
            Tour t1 = bip.getEdgeSource(e);
            Tour t2 = bip.getEdgeTarget(e);
            if (Math.abs(t1.getId()) == Math.abs(t2.getId())) {
                q++;
            }
            Repair repair = repair(t1, t2);
            repairedTours.add(repair.getRepairedTours().get(0));
            repair.getRepairedTours().size();
        }

        Solution rep = new Solution(model);
        for (Tour t : repairedTours) {
            for (int i = 0; i < t.size() - 1; i++) {
                rep.set(t.get(i), t.get(i + 1), 1);
            }
        }
        return rep;
    }

    /**
     *
     * @return
     */
    public Solution getSolution() {
        List<Solution> solutions = model.getSolutions();
        Solution bestSol = null;
        int bestVal = Integer.MAX_VALUE;
        for (Solution sol : solutions) {
            Solution rep = repair(sol);
            int val = rep.totalCost();
            if (val < bestVal) {
                bestSol = rep;
                bestVal = val;
            }
        }
        return bestSol;
    }

}
