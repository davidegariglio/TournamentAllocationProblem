import ilog.concert.*;
import ilog.cplex.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Random;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

public class Main {
	@SuppressWarnings("unused")
	private static String year = "2017";
	// 520: ROL - 540: WIM - 560: US - 580: AUS
	@SuppressWarnings("unused")
	private static String Tournament_id = "580";
	// ATP or WTA
	private static String[] Type = { "WTA" };
	private static Double eta = 0.9;
	private static Boolean LocalSearch = false;

	private static int k = 4;
	private static int v = 32;
	private static String Path = "";
	private static Boolean Verbose = false;
	public static long Clock;

	private static void TimeElapsed() {
		System.out.println("Time elapsed:" + (System.nanoTime() - Clock) / 1000000000F + "s");
	}

	private static Boolean isOne(Double var) {
		if (var > 0.9 && var < 1.1)
			return true;
		else
			return false;
	}

	private static Map<String, Double> sortByValue(Map<String, Double> unsortMap) {
		// Sort Map by value
		// Based on https://www.mkyong.com/java/how-to-sort-a-map-in-java/
		LinkedList<Entry<String, Double>> list = new LinkedList<Map.Entry<String, Double>>(unsortMap.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
			public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
				return -(o1.getValue()).compareTo(o2.getValue());
			}
		});
		Map<String, Double> sortedMap = new LinkedHashMap<String, Double>();
		for (Map.Entry<String, Double> entry : list) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}
		return sortedMap;
	}

	private static void auxOutput(String torunament_name, String year_in, String unique, String t)
			throws ErrorThrower, FileNotFoundException {

		Path = "out/" + unique + "/" + year_in + "-" + t + "_" + torunament_name + "/";
		File f = new File(Path);
		f.mkdirs();
	}

	@SuppressWarnings({ "unchecked", "unlikely-arg-type" })
	public static void main(String[] args) throws ErrorThrower, SQLException, IOException, ParseException {

		long Main = System.nanoTime();
		// String[] years = { "2014", "2015", "2016", "2017" };
		String[] ids = {"580","520","540","560" };
		String[] years = { "2017" };
		String[] Type = { "WTA", "ATP" };
		Calendar now = Calendar.getInstance();
		String unique = now.get(Calendar.HOUR_OF_DAY) + "_" + now.get(Calendar.MINUTE);
		String mainPath = "out/" + unique + "/";
		File filez = new File(mainPath);
		filez.mkdirs();
		BufferedWriter writerMain = new BufferedWriter(new FileWriter(mainPath + "Results.csv"));
		@SuppressWarnings("resource")
		CSVPrinter csvPrinterMain = new CSVPrinter(writerMain,
				CSVFormat.DEFAULT.withHeader("", "Time", "O.F.", "Status", "O.F. (HeuTime)", "Unlucky Pairings",
						"Positive costs (1R)", "Costs (1R)", "Positive costs (T)", "Costs (T)"));

		FileOutputStream fout = new FileOutputStream(mainPath + "Instance.txt");
		MultiOutputStream multiOut = new MultiOutputStream(System.out, fout);
		PrintStream stdout = new PrintStream(multiOut);
		System.setOut(stdout);

		for (int Ty = 0; Ty < Type.length; Ty++) {
			for (int q = 0; q < years.length; q++) {
				for (int y = 0; y < ids.length; y++) {
					Clock = System.nanoTime();
					Random randomGenerator = new Random();
					DecimalFormat df = new DecimalFormat();
					df.setMaximumFractionDigits(2);
					df.setMinimumFractionDigits(2);

					TennisData TData = new TennisData(ids[y], years[q], Type[Ty], false);
					auxOutput(ids[y] + "-" + TData.getTorunament_name(), years[q], unique, Type[Ty]);

					Double h[][] = TData.loadH();
					String h_desc[][] = TData.loadH_desc();
					Double p[][] = TData.loadP();

					HashMap<String, Double> misfortune_map = TData.getMisfortune_map();
					int[] misfortune_count = TData.getMisfortune_count();
					ArrayList<Player> players = TData.getPlayers();

					System.out.println(
							"Dataset loaded for " + TData.getTorunament_name() + " " + TData.getTournament_year());
					System.out.println("\tTournament size (n): " + TData.getTournament_size());
					System.out.println("\tSeed size (m): " + TData.getSeeds());
					System.out.println("\tSeasonal misfortune:");
					for (int i = 0; i < misfortune_count.length; i++)
						System.out.println("\t\t of " + i + ": " + misfortune_count[i] + " players.");

					TimeElapsed();

					// TournamentAllocationProblem parameters
					int n = TData.getTournament_size();
					int m = TData.getSeeds();
					System.out.println(n + " " + m);

					if ((n % 2) != 0 || (m % 2) != 0 || ((n / k) % 2) != 0) {
						throw new ErrorThrower("n,m must be even, and mod(n/k)=0");
					}
					if (m >= (n - 1)) {
						throw new ErrorThrower("Well... I bet you don't need a this tool :-)");
					}

					int u = n / k; // Amount of players per cluster
					int f = m / k; // Amount of seeds per cluster
					int s = 1000; // Number of simulations performed for the whole tournament
					int rounds = (int) (Math.log(n) / Math.log(2));
					Double LB = -1.0; // Lower Bound for the problem

					int ACTUAL_FirstRound[][] = new int[n][k]; // |ACTUAL| Cluster in actual tournament
					int ACTUAL_Rounds[][] = TData.getRealTournament(); // |ACTUAL| Tournament representation

					int CPLEX_FirstDraw[] = new int[n]; // |CPLEX| Draw for first round
					int CPLEX_Draws[][] = new int[n][rounds]; // |CPLEX| Simulations for other rounds

					int GreedySolution[][] = new int[n][k]; // |CONST| X_ij for greedy solution
					int GreedyCurrent[][] = new int[n][k];
					int GreedyBest[][] = new int[n][k];
					int GreedyBase[][] = new int[n][k];
					int GREEDY_FirstDraw[] = new int[n]; // |CONST| Draw for first round
					int GREEDY_Draws[][] = new int[n][rounds]; // |CONST| Simulations for other rounds

					// CPLEX Instance
					try {
						IloCplex cplex = new IloCplex();
						IloIntVar[][] x = new IloIntVar[n][];
						for (int i = 0; i < n; i++) {
							x[i] = cplex.intVarArray(k, 0, 1);
						}
						// Reset the greedy's solution
						for (int i = 0; i < n; i++)
							for (int j = 0; j < k; j++) {
								GreedyBase[i][j] = -1;
								x[i][j].setName("x_" + i + "-" + j + "");
							}
						int seeds = 0;
						for (int i = 0; i < players.size(); i++) {
							for (int j = 0; j < k; j++) {
								if (players.get(i).getSeed() > 0) {

									// Seeding in the correct position
									if (players.get(i).getSeedposition() >= ((j * u) + 1)
											&& players.get(i).getSeedposition() <= ((j + 1) * u)
											&& players.get(i).getHas_seed() == false) {
										players.get(i).setHas_seed(true);
										x[i][j] = cplex.intVar(1, 1);
										players.get(i).setCluster(j);
										for (int j_s = 0; j_s < k; j_s++)
											if (j_s != j)
												cplex.add(cplex.eq(x[i][j_s], 0));
										GreedyBase[i][j] = 1;
										seeds++;
										if (Verbose)
											System.out.println("\t" + players.get(i).getName() + " in cluster " + j);
									}
								}
								// Represent in the actual draw cluster matrix
								// match numbers in ATP Database start from 100
								if ((players.get(i).getMatch_num() - 99) >= ((j * u / 2) + 1)
										&& (players.get(i).getMatch_num() - 99) <= ((j + 1) * u / 2)) {
									ACTUAL_FirstRound[i][j] = 1;
								}
							}

						}
						System.out.println("Number of seeds: " + seeds);

						// Constraint for unfortunate players
						IloLinearNumExpr[] UnluckyPerCluster = new IloLinearNumExpr[k];
						// Generate expression for maximum unlucky players per cluster
						for (int j = 0; j < k; j++)
							UnluckyPerCluster[j] = cplex.linearNumExpr();

						int misfortunate_count = 0;
						Map<String, Double> misfortune_sorted = sortByValue(misfortune_map);
						int[] misfortunate_ids = new int[v];
						int current_misfortunate = -1;

						for (Map.Entry<String, Double> e : misfortune_sorted.entrySet()) {
							if (misfortunate_count < v) {
								current_misfortunate = Integer.parseInt(e.getKey());
								if (!(players.get(current_misfortunate).getSeed() > 0)) {
									for (int j = 0; j < k; j++)
										UnluckyPerCluster[j].addTerm(1.0, x[current_misfortunate][j]);

									players.get(current_misfortunate).setUnlucky(true);
									misfortunate_ids[misfortunate_count] = current_misfortunate;
									misfortunate_count++;
								}
							}
						}

						for (int j = 0; j < k; j++) {
							cplex.addUserCut(cplex.eq(UnluckyPerCluster[j], v / k));
						}

						if (Verbose)
							System.out.println("Resetting h coefficients between unlucky and seeds");

						// Reset mutual h-coefficients with seeds and unlucky
						for (int j = 0; j < v; j++) {
							for (int i = 0; i < players.size(); i++) {
								if (players.get(i).getSeed() > 0 && i != misfortunate_ids[j]) {
									if (Verbose)
										System.out.println("\tResetting h for " + players.get(i).getName() + " and "
												+ players.get(misfortunate_ids[j]).getName());
									h[misfortunate_ids[j]][i] = 0.0;
									h[i][misfortunate_ids[j]] = 0.0;
								}
							}
						}
						// Reset mutual h-coefficients with seeds in different cluster
						for (int i = 0; i < players.size(); i++) {
							for (int j = 0; j < players.size(); j++) {
								if (players.get(i).getHas_seed() && players.get(j).getHas_seed()
										&& players.get(i).getCluster() != players.get(j).getCluster()) {
									h[i][j] = 0.00;
									h[j][i] = 0.00;
									if (Verbose)
										System.out.println("Resetting for " + players.get(i).getName() + " and "
												+ players.get(j).getName());
								}

							}
						}

						System.out.println("Number of unlucky: " + misfortunate_count);
						System.out.println("");

						// Compute the lower bound with seeds
						for (int j = 0; j < k; j++) {
							if (Verbose)
								System.out.println("\tCLUSTER " + j + " | ------------------");
							for (int alfa = 0; alfa < n; alfa++) {
								for (int beta = 0; beta < n; beta++) {
									if (alfa > beta) {
										if (GreedyBase[alfa][j] > 0 && GreedyBase[beta][j] > 0) {
											LB += h[alfa][beta];
											if (Verbose && h[alfa][beta] > 0)
												System.out.println("\t" + players.get(alfa).getName() + " and "
														+ players.get(beta).getName() + ":+" + h[alfa][beta]);
										}
									}
								}
							}
						}
						// System.out.println("Lower bound for this problem is: " + LB);

						// Compute the objective function for the actual draw
						// As to compare the delta with CPLEX and greedy

						Double objective_actual = 0.0;
						for (int j = 0; j < k; j++) {
							for (int alfa = 0; alfa < n; alfa++) {
								for (int beta = 0; beta < n; beta++) {
									if (alfa < beta) {
										objective_actual += ACTUAL_FirstRound[alfa][j] * ACTUAL_FirstRound[beta][j]
												* h[alfa][beta];
									}
								}
							}
						}

						// Greedy solution
						// Based on degrees of players
						System.out.println("Building the greedy solution.");
						float GR_start = System.nanoTime();
						System.out.println("\tBuilding degree matrixes for H...");
						Double h_Degree[][] = new Double[n][n]; // Matrix of degrees

						// Computing the degree matrix for H
						Double degree = 0.0;
						Double weighted_degree = 0.0;
						// The hash map will be sorted as to be used for the greedy algorithm
						HashMap<String, Double> degree_map_weighted = new HashMap<String, Double>();
						Double H_max_degree = 0.0, H_min_degree = 0.0;
						Double H_max_Wdegree = 0.0, H_min_Wdegree = 0.0;
						Double AVG_degree = 0.00, AVG_Wdegree = 0.00;
						Double h_max = 0.00;
						int Q = 0;
						for (int i = 0; i < n; i++) {
							degree = 0.0;
							weighted_degree = 0.0;
							for (int j = 0; j < n; j++) {
								weighted_degree += h[i][j];
								if (h_max < h[i][j])
									h_max = h[i][j];
								if (h[i][j] > 0)
									degree++;
							}
							if (degree == 0) {
								Q++;
							}
							h_Degree[i][i] = degree;
							players.get(i).setDegree(degree);
							degree_map_weighted.put(Integer.toString(i), weighted_degree);
							AVG_degree += degree;
							AVG_Wdegree += weighted_degree;
							if (i == 0) {
								H_max_degree = degree;
								H_min_degree = degree;
								H_max_Wdegree = weighted_degree;
								H_min_Wdegree = weighted_degree;
							}
							if (players.get(i).getUnlucky() && Verbose) {
								System.out.println("Unlucky player " + players.get(i).getName() + " ("
										+ misfortune_map.get(Integer.toString(i)) + ")");
								System.out.println("\tDegree: " + degree + " - WDegree: " + weighted_degree);
								for (int j = 0; j < n; j++) {
									if (h[i][j] > 0)
										System.out.println("\t\tH of " + h[i][j] + " with " + players.get(j).getName());
								}
							}
							if (degree > H_max_degree)
								H_max_degree = degree;
							if (degree < H_min_degree && degree != 0.0)
								H_min_degree = degree;
							if (weighted_degree > H_max_Wdegree)
								H_max_Wdegree = weighted_degree;
							if (weighted_degree < H_min_Wdegree && weighted_degree != 0.0)
								H_min_Wdegree = weighted_degree;
						}
						AVG_degree = AVG_degree / n;
						AVG_Wdegree = AVG_Wdegree / n;
						// Sorting the hash map
						Map<String, Double> degree_map_weighted_sorted = sortByValue(degree_map_weighted);
						Double objective_greedy = 0.0;
						float greedy_time = 0;
						float GR_time = 0;
						Double best_obj_greedy = 0.0;
						Double delta_Sum = 0.0;
						Double delta_Sum_gr = 0.0;
						float GR_time_first = 0;
						Boolean LocalLimit = true;
						for (int a = 0; a < s && LocalLimit; a++) {
							// Fill -1 values with zero.
							for (int i = 0; i < n; i++)
								for (int j = 0; j < k; j++)
									GreedyCurrent[i][j] = GreedyBase[i][j];
							objective_greedy = 0.0;
							greedy_time = 0;
							GR_time = 0;

							int id = -1;
							Double current_obj = 0.0, current_min_obj = 0.0;
							int min_cluster = 0;
							int free_in_cluster[] = new int[k];
							int unlucky_in_cluster[] = new int[k];
							Boolean first_cl = true;
							int itcount = 0;
							int random_cl = 0;
							for (int j = 0; j < k; j++)
								free_in_cluster[j] = u - f - v / k;
							for (int j = 0; j < k; j++)
								unlucky_in_cluster[j] = v / k;

							itcount = 0;
							for (Map.Entry<String, Double> e : degree_map_weighted_sorted.entrySet()) {
								id = Integer.parseInt(e.getKey());
								if (!players.get(id).getHas_seed() && players.get(id).getUnlucky()) {
									min_cluster = -1;
									first_cl = true;
									for (int j = 0; j < k; j++) {
										current_obj = 0.0;
										if (unlucky_in_cluster[j] > 0 && players.get(id).getUnlucky()) {
											for (int ii = 0; ii < n; ii++) {
												if (GreedyCurrent[ii][j] >= 0)
													current_obj += h[ii][id];
											}
											if (first_cl) {
												current_min_obj = current_obj + 1;
												first_cl = false;
											}
											if (current_obj < current_min_obj) {
												current_min_obj = current_obj;
												min_cluster = j;
											}

										}
									}
									//if (Math.random() > eta && itcount < (n - m - 1) && a == 0) {
									if (Math.random() > eta && itcount < (n - m - 1)) {
										random_cl = min_cluster;
										while (random_cl == min_cluster || unlucky_in_cluster[random_cl] <= 0
												|| min_cluster == -1)
											random_cl = (int) Math
													.rint(Math.random() * min_cluster + Math.random() * 100) % k;
										System.out.println(
												"\tRandomized assignation triggered for " + players.get(id).getName()
														+ ". Moving from " + min_cluster + " to " + random_cl);
										min_cluster = random_cl;
									}
									// System.out
									// .println("\tAssigning " + players.get(id).getName() + " to cluster " +
									// min_cluster);
									GreedyCurrent[id][min_cluster] = 1;
									unlucky_in_cluster[min_cluster]--;

								}
								if (!players.get(id).getHas_seed() && !players.get(id).getUnlucky()) {
									min_cluster = -1;
									first_cl = true;
									for (int j = 0; j < k; j++) {
										current_obj = 0.0;
										if (free_in_cluster[j] > 0)

										{
											for (int ii = 0; ii < n; ii++) {
												if (GreedyCurrent[ii][j] >= 0)
													current_obj += h[ii][id];
											}
											if (first_cl) {
												current_min_obj = current_obj + 1;
												first_cl = false;
											}
											if (current_obj < current_min_obj) {
												current_min_obj = current_obj;
												min_cluster = j;
											}

										}
									}
									//if (Math.random() > eta && itcount < (n - m - 1) && a == 0) {
									if (Math.random() > eta && itcount < (n - m - 1)) {
										random_cl = min_cluster;
										while (random_cl == min_cluster || free_in_cluster[random_cl] <= 0
												|| min_cluster == -1)
											random_cl = (int) Math
													.rint(Math.random() * min_cluster + Math.random() * 100) % k;
										System.out.println(
												"\tRandomized assignation triggered for " + players.get(id).getName()
														+ ". Moving from " + min_cluster + " to " + random_cl);
										min_cluster = random_cl;
									}
									// System.out
									// .println("\tAssigning " + players.get(id).getName() + " to cluster " +
									// min_cluster);
									GreedyCurrent[id][min_cluster] = 1;
									free_in_cluster[min_cluster]--;

								}
								itcount++;
							}
							// Fill -1 values with zero.
							for (int i = 0; i < n; i++)
								for (int j = 0; j < k; j++)
									if (GreedyCurrent[i][j] == -1)
										GreedyCurrent[i][j] = 0;

							// Local search: swap 2 elements and compute an eventual positive O.F.
							// So, with Hamming distance of two try to find good swaps
							// Create a bucket for this swapping which excludes seeded players
							ArrayList<Integer> bucket_switch = new ArrayList<Integer>();
							for (int ii = 0; ii < n; ii++) {
								if (!players.get(ii).getHas_seed()) {
									bucket_switch.add(ii);
								}
							}

							Double TL_greedy = 0.75;
							float greedy_start = System.nanoTime();
							greedy_time = 0;
							int j_current_Alpha = -1, j_current_Beta = -1, index_Alpha = -1, index_Beta = -1,
									Alpha = -1, Beta = -1;
							Double delta_First = 0.0;
							Double delta_Second = 0.0;
							delta_Sum = 0.0;
							while (greedy_time < TL_greedy) {
								// INITIALIZE
								Beta = Alpha = index_Alpha = index_Beta = j_current_Beta = j_current_Alpha = -1;
								delta_First = delta_Second = 0.0;
								Boolean prop = true;

								// Get two random elements
								// Iterate until they belong to different clusters
								// And they are of the same type
								while (j_current_Alpha == j_current_Beta || prop) {
									index_Alpha = randomGenerator.nextInt(bucket_switch.size());
									Alpha = bucket_switch.get(index_Alpha);
									index_Beta = randomGenerator.nextInt(bucket_switch.size());
									Beta = bucket_switch.get(index_Beta);
									if (Alpha != Beta) {
										for (int j = 0; j < k; j++) {
											if (GreedyCurrent[Alpha][j] == 1)
												j_current_Alpha = j;
											if (GreedyCurrent[Beta][j] == 1)
												j_current_Beta = j;
										}
									}
									if (players.get(Alpha).getUnlucky() == players.get(Beta).getUnlucky())
										prop = false;
									else
										prop = true;
								}
								// Once players are selected, try to swap them
								// In Beta's cluster
								for (int ii = 0; ii < n; ii++) {
									if (GreedyCurrent[ii][j_current_Beta] == 1) {
										// Player is in Beta's cluster
										// Compute delta
										if (ii != Beta)
											delta_First += h[Alpha][ii];
										delta_First -= h[Beta][ii];
									}
								}
								for (int ii = 0; ii < n; ii++) {
									if (GreedyCurrent[ii][j_current_Alpha] == 1) {
										// Player is in Beta's cluster
										// Compute delta
										if (ii != Alpha)
											delta_Second += h[Beta][ii];
										delta_Second -= h[Alpha][ii];
									}
								}
								if ((delta_First + delta_Second) < 0) {
									delta_Sum += delta_First + delta_Second;
									GreedyCurrent[Alpha][j_current_Alpha] = 0;
									GreedyCurrent[Alpha][j_current_Beta] = 1;
									GreedyCurrent[Beta][j_current_Beta] = 0;
									GreedyCurrent[Beta][j_current_Alpha] = 1;
									// System.out.println("Found a good swap between " +
									// players.get(Alpha).getName() + " and "
									// + players.get(Beta).getName() + " with delta=" + (delta_First +
									// delta_Second));
								}
								greedy_time = (System.nanoTime() - greedy_start) / 1000000000F;
							}
							if (a == 0)
								System.out.println("\tSwaps executed in " + df.format(greedy_time)
										+ "s with a total delta of " + delta_Sum);
							GR_time = (System.nanoTime() - GR_start) / 1000000000F;
							objective_greedy = 0.0;
							for (int j = 0; j < k; j++) {
								for (int alfa = 0; alfa < n; alfa++) {
									for (int beta = 0; beta < n; beta++) {
										if (GreedyCurrent[alfa][j] != -1 && GreedyCurrent[beta][j] != -1)
											if (alfa < beta) {
												objective_greedy += GreedyCurrent[alfa][j] * GreedyCurrent[beta][j]
														* h[alfa][beta];
											}
									}
								}
							}
							if (a == 0) {

								System.out.println("First Greedy solution built in " + GR_time + "s has an O.F. of: "
										+ objective_greedy);

								for (int i = 0; i < n; i++)
									for (int j = 0; j < k; j++)
										GreedySolution[i][j] = GreedyCurrent[i][j];

								best_obj_greedy = objective_greedy;
								delta_Sum_gr = delta_Sum;
								GR_time_first = GR_time;
							}
							if (!LocalSearch) {
								if (a == 1) {
									LocalLimit = false;
								}
							} else
								System.out.println("Generating more Greedy solutions...");

							if (best_obj_greedy > objective_greedy) {
								for (int i = 0; i < n; i++)
									for (int j = 0; j < k; j++)
										GreedyBest[i][j] = GreedyCurrent[i][j];
								best_obj_greedy = objective_greedy;
							}
						}
						System.out.println("Best Greedy with OF of:" + best_obj_greedy);
						for (int j = 0; j < k; j++) {
							int count = 0;
							int count2 = 0;
							int count3 = 0;
							int deg = 0;
							int wdeg = 0;
							for (int i = 0; i < n; i++) {
								if (GreedySolution[i][j] == 1) {
									count2++;
									deg += h_Degree[i][i];
									wdeg += degree_map_weighted.get(Integer.toString(i));
									String qual = players.get(i).isnotQualified() ? " "
											: " (" + players.get(i).getEntry() + ") ";
									String seed = players.get(i).getHas_seed()
											? " (Seeded as " + players.get(i).getSeed() + ") "
											: " ";
									if (Verbose)
										System.out.println("\t(" + i + ")\t\tDEG=" + h_Degree[i][i] + "(w:"
												+ degree_map_weighted.get(Integer.toString(i)) + ")\t\t\t\t"
												+ players.get(i).getName() + qual + seed);
									if (players.get(i).getSeed() > 0)
										count++;
									if (players.get(i).getUnlucky())
										count3++;

								}
							}
							if (Verbose)
								System.out.println("Cluster " + j + " has a total of " + count2 + " players\n \twith "
										+ count + " seeds\n\twith " + count3 + " unlucky players");
							if (Verbose)
								System.out.println("Avg degree:" + df.format(deg / count2) + " and wdegree:"
										+ df.format(wdeg / count2) + "\n");

						}
						TimeElapsed();

						System.out.println("Using CPLEX to compute a solution.");
						// Initialize expressions for constraints generation
						IloLinearNumExpr[] PlayersPerCluster = new IloLinearNumExpr[k];
						IloLinearNumExpr[] PlayerMaxAssignations = new IloLinearNumExpr[n];

						// Generate expression for maximum players per cluster
						for (int j = 0; j < k; j++) {
							PlayersPerCluster[j] = cplex.linearNumExpr();
							for (int i = 0; i < n; i++) {
								PlayersPerCluster[j].addTerm(1.0, x[i][j]);
							}
						}

						// Generate expression for maximum player assignations (1)
						for (int i = 0; i < n; i++) {
							PlayerMaxAssignations[i] = cplex.linearNumExpr();
							for (int j = 0; j < k; j++) {
								PlayerMaxAssignations[i].addTerm(1.0, x[i][j]);
							}
						}

						// Add constraints to the model basing on expressions
						for (int j = 0; j < k; j++) {
							cplex.addEq(PlayersPerCluster[j], u);
						}
						for (int i = 0; i < n; i++) {
							cplex.addEq(PlayerMaxAssignations[i], 1);
						}

						// Define objective function
						IloNumExpr objective = cplex.numExpr();
						// Generate expression for objective function
						// Since h-matrix is symmetric, there are e= n^2-n significant values
						// which need to be taken into account.

						for (int j = 0; j < k; j++) {
							for (int alfa = 0; alfa < n; alfa++) {
								for (int beta = 0; beta < n; beta++) {
									if (alfa != beta && alfa > beta) {
										objective = cplex.sum(objective,
												cplex.prod(h[alfa][beta], cplex.prod(x[alfa][j], x[beta][j])));
									}
								}
							}
						}
						cplex.addMinimize(objective);

						System.out.println("\tImporting greedy solution as the starting one.");
						IloIntVar[] c_startVar = new IloIntVar[n * k];
						double[] c_startVal = new double[n * k];
						for (int i = 0, idx = 0; i < n; ++i) {
							for (int j = 0; j < k; ++j) {
								c_startVar[idx] = x[i][j];
								c_startVal[idx] = GreedyBest[i][j];
								++idx;
							}
						}
						cplex.addMIPStart(c_startVar, c_startVal);
						cplex.writeMIPStarts(Path + "Greedy.mst");
						cplex.deleteMIPStarts(0);
						cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
						cplex.setParam(IloCplex.Param.TimeLimit, 600);
						/*
						 * cplex.setParam(IloCplex.Param.OptimalityTarget, 1);
						 * cplex.setParam(IloCplex.Param.Emphasis.MIP, 2);
						 * cplex.setParam(IloCplex.Param.MIP.Strategy.Search, 1);
						 * cplex.setParam(IloCplex.Param.TimeLimit, 300);
						 * cplex.setParam(IloCplex.Param.Parallel, -1);
						 * cplex.setParam(IloCplex.Param.Threads, 8);
						 */
						cplex.exportModel(Path + "Model.lp");

						// Separate instance to test the solving time
						// Starting from the greedy solution

						Double CPLEX_sameTime = 0.0;

						System.gc();

						System.out.println("Testing CPLEX with same time used by GREEDY.");
						IloCplex cplex_greedy_time = new IloCplex();
						cplex_greedy_time.importModel(Path + "Model.lp");
						cplex_greedy_time.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
						cplex_greedy_time.setParam(IloCplex.Param.TimeLimit, GR_time_first);
						cplex_greedy_time.setParam(IloCplex.Param.OptimalityTarget, 1);
						if (cplex_greedy_time.solve()) {
							CPLEX_sameTime = cplex_greedy_time.getObjValue();
							System.out.println("CPLEX found in " + GR_time + " a solution with O.F." + CPLEX_sameTime);
						}

						// Solver
						boolean solver = false;
						float CPLEX_start = System.nanoTime();
						solver = cplex.solve();

						// Solve
						float CPLEX_time = 0;
						if (solver) {
							CPLEX_time = (System.nanoTime() - CPLEX_start) / 1000000000F;
							System.out.println("CPLEX solution status: " + cplex.getStatus());
							cplex.writeSolutions(Path + "Solution.SOL");
							int count2;

							for (int j = 0; j < k; j++) {
								int count = 0;
								count2 = 0;
								Double deg = 0.0;
								Double wdeg = 0.0;
								System.out.println("CLUSTER " + j + "------------------------");
								for (int i = 0; i < n; i++) {
									if (isOne(cplex.getValue(x[i][j]))) {
										count2++;
										deg += h_Degree[i][i];
										wdeg += degree_map_weighted.get(Integer.toString(i));
										String qual = players.get(i).isnotQualified() ? " "
												: " (" + players.get(i).getEntry() + ") ";
										String seed = players.get(i).getHas_seed()
												? " (Seeded as " + players.get(i).getSeed() + ") "
												: " ";
										if (Verbose)
											System.out.println("\t(" + i + ")\t\tDEG=" + h_Degree[i][i] + "(w:"
													+ degree_map_weighted.get(Integer.toString(i)) + ")\t\t\t\t"
													+ players.get(i).getName() + qual + seed);
										if (players.get(i).getSeed() > 0) {
											count++;
										}
									}
								}
								System.out.println("Cluster " + j + " has a total of " + count2 + " players with "
										+ count + " seeds.");
								System.out.println("Avg degree:" + df.format(deg / count2) + " and wdegree:"
										+ df.format(wdeg / count2) + "\n");
							}

							// Average indexes for simulations
							// cindex-es: measures of conflicts
							// nconf-s: amounts of conflicts

							Double AVG_cindex_cplex = 0.0;
							Double AVG_TOURNEY_cplex_cindex = 0.0;
							Double AVG_TOURNEY_cplex_nconf = 0.0;
							Double AVG_nconf_cplex = 0.0;
							Double AVG_unlucky_cplex = 0.0;

							Double AVG_cindex_greedy = 0.0;
							Double AVG_TOURNEY_greedy_cindex = 0.0;
							Double AVG_TOURNEY_greedy_nconf = 0.0;
							Double AVG_nconf_greedy = 0.0;
							Double AVG_unlucky_greedy = 0.0;

							// Indexes for last simulation
							Double cindex_original = 0.0;
							int nconf_original = 0;
							int nconf_unlucky_original = 0;
							Double TOURNEY_cindex_original = 0.0;
							int TOURNEY_nconf_original = 0;
							Double cindex_cplex = 0.0;
							int nconf_cplex = 0;
							Double TOURNEY_cindex_cplex = 0.0;
							int TOURNEY_nconf_cplex = 0;
							Double cindex_greedy = 0.0;
							int nconf_greedy = 0;
							Double TOURNEY_cindex_greedy = 0.0;
							int TOURNEY_nconf_greedy = 0;
							String[] CPLEX_Winners = new String[s];
							String[] GREEDY_Winners = new String[s];

							System.out.println(
									"Generating draws for the first round both for CPLEX and greedy solution.");
							System.out.println("For each (1st round) draw generated, the whole tourney is simulated.");
							System.out.println("This process may take a while...");
							TimeElapsed();

							for (int z = 0; z < s; z++) {

								for (int i = 0; i < n; i++) {
									CPLEX_FirstDraw[i] = -1;
									GREEDY_FirstDraw[i] = -1;
								}

								ArrayList<Integer>[] bucket = (ArrayList<Integer>[]) new ArrayList[k];
								ArrayList<Integer>[] bucket_greedy = (ArrayList<Integer>[]) new ArrayList[k];
								ArrayList<Integer>[] bucket_unlucky = (ArrayList<Integer>[]) new ArrayList[k];
								ArrayList<Integer>[] bucket_greedy_unlucky = (ArrayList<Integer>[]) new ArrayList[k];
								for (int j = 0; j < k; j++) {
									count2 = 0;
									bucket[j] = new ArrayList<Integer>();
									bucket_greedy[j] = new ArrayList<Integer>();
									bucket_unlucky[j] = new ArrayList<Integer>();
									bucket_greedy_unlucky[j] = new ArrayList<Integer>();
									for (int i = 0; i < n; i++) {
										if (isOne(cplex.getValue(x[i][j]))) {
											if (players.get(i).getSeed() > 0)
												CPLEX_FirstDraw[players.get(i).getSeedposition() - 1] = i;
											else {
												if (players.get(i).getUnlucky())
													bucket_unlucky[j].add(i);
												else
													bucket[j].add(i);
											}
										}
										if (GreedySolution[i][j] == 1) {
											if (players.get(i).getSeed() > 0)
												GREEDY_FirstDraw[players.get(i).getSeedposition() - 1] = i;
											else {
												if (players.get(i).getUnlucky())
													bucket_greedy_unlucky[j].add(i);
												else
													bucket_greedy[j].add(i);
											}
										}
									}

								}
								int randomIndex = -1, randomIndex_c = -1;
								int against = -1;

								for (int j = 0; j < k; j++) {
									for (int i = 0; i < u; i++) {
										randomIndex = -1;
										if (CPLEX_FirstDraw[j * u + i] < 0) {
											if (bucket[j].size() > 0) {
												if ((j * u + i) % 2 == 0)
													against = j * u + i + 1;
												else
													against = j * u + i - 1;
												if (CPLEX_FirstDraw[against] > -1
														&& players.get(CPLEX_FirstDraw[against]).getHas_seed()) {
													randomIndex = randomGenerator.nextInt(bucket[j].size());
													CPLEX_FirstDraw[j * u + i] = bucket[j].get(randomIndex);
													bucket[j].remove(randomIndex);
												}
											}
										}

										if (GREEDY_FirstDraw[j * u + i] < 0) {
											if (bucket_greedy[j].size() > 0) {
												if ((j * u + i) % 2 == 0)
													against = j * u + i + 1;
												else
													against = j * u + i - 1;
												if (GREEDY_FirstDraw[against] > -1
														&& players.get(GREEDY_FirstDraw[against]).getHas_seed()) {
													randomIndex = randomGenerator.nextInt(bucket_greedy[j].size());
													GREEDY_FirstDraw[j * u + i] = bucket_greedy[j].get(randomIndex);
													bucket_greedy[j].remove(randomIndex);
												}
											}
										}

									}
								}
								for (int j = 0; j < k; j++) {
									bucket[j].addAll(bucket_unlucky[j]);
									bucket_greedy[j].addAll(bucket_greedy_unlucky[j]);
								}

								for (int j = 0; j < k; j++) {
									for (int i = 0; i < u; i++) {
										randomIndex = -1;

										if (CPLEX_FirstDraw[j * u + i] < 0) {
											if (bucket[j].size() > 0) {
												randomIndex = randomGenerator.nextInt(bucket[j].size());
												CPLEX_FirstDraw[j * u + i] = bucket[j].get(randomIndex);
												bucket[j].remove(randomIndex);
											}
										}

										if (GREEDY_FirstDraw[j * u + i] < 0) {
											if (bucket_greedy[j].size() > 0) {
												randomIndex_c = randomGenerator.nextInt(bucket_greedy[j].size());
												GREEDY_FirstDraw[j * u + i] = bucket_greedy[j].get(randomIndex_c);
												bucket_greedy[j].remove(randomIndex_c);
											}
										}
									}
								}
								for (int i = 0; i < (n / 2); i++) {

									AVG_cindex_cplex += h[CPLEX_FirstDraw[i * 2]][CPLEX_FirstDraw[i * 2 + 1]];
									if (h[CPLEX_FirstDraw[i * 2]][CPLEX_FirstDraw[i * 2 + 1]] > 0)
										AVG_nconf_cplex++;

									if ((players.get(CPLEX_FirstDraw[i * 2]).getUnlucky()
											&& players.get(CPLEX_FirstDraw[i * 2 + 1]).getHas_seed())
											|| (players.get(CPLEX_FirstDraw[i * 2 + 1]).getUnlucky()
													&& players.get(CPLEX_FirstDraw[i * 2]).getHas_seed()))
										AVG_unlucky_cplex++;

									AVG_cindex_greedy += h[GREEDY_FirstDraw[i * 2]][GREEDY_FirstDraw[i * 2 + 1]];
									if (h[GREEDY_FirstDraw[i * 2]][GREEDY_FirstDraw[i * 2 + 1]] > 0)
										AVG_nconf_greedy++;

									if ((players.get(GREEDY_FirstDraw[i * 2]).getUnlucky()
											&& players.get(GREEDY_FirstDraw[i * 2 + 1]).getHas_seed())
											|| (players.get(GREEDY_FirstDraw[i * 2 + 1]).getUnlucky()
													&& players.get(GREEDY_FirstDraw[i * 2]).getHas_seed()))
										AVG_unlucky_greedy++;
								}
								if (z == (s - 1)) {
									if (Verbose) {
										System.out.println("");
										System.out.println("Last 1stR draw with the CPLEX's solution:");
										System.out.println(
												"Penalty types: 1 (first round) - 2 (second round) - 3 (third round) - L (Q and SF rounds) - C (country).");
									}
									for (int j = 0; j < k; j++) {
										if (Verbose)
											System.out.println("\t--CLUSTER " + j + "--\n");
										for (int i = 0; i < u; i++) {
											String qual = players.get(CPLEX_FirstDraw[j * u + i]).isnotQualified() ? ""
													: " (" + players.get(CPLEX_FirstDraw[j * u + i]).getEntry() + ") ";
											String seed = players.get(CPLEX_FirstDraw[j * u + i]).getHas_seed()
													? " (Seeded as " + players.get(CPLEX_FirstDraw[j * u + i]).getSeed()
															+ ") "
													: "";
											String unl = players.get(CPLEX_FirstDraw[j * u + i]).getUnlucky()
													? " (Unlucky) "
													: "";
											if (Verbose)
												System.out.println("\t " + (j * u + i + 1) + ".\t"
														+ players.get(CPLEX_FirstDraw[j * u + i]).getName() + qual
														+ seed + unl);
											if ((j * u + i + 1) % 2 == 1) {
												if (Verbose)
													System.out
															.println("\t/\n------- Penalty_type(s): "
																	+ h_desc[CPLEX_FirstDraw[j * u
																			+ i]][CPLEX_FirstDraw[j * u + i + 1]]
																	+ "\n\t\\");
											} else {
												if (Verbose)
													System.out.println("");
											}
										}
									}
									if (Verbose) {
										System.out.println("");
										System.out.println("");
										System.out.println("Last1 1stR draw with the solution found with greedy:");
										System.out.println(
												"Penalty types: 1 (first round) - 2 (second round) - 3 (third round) - L (Q and SF rounds) - C (country).");
									}
									for (int j = 0; j < k; j++) {
										if (Verbose)
											System.out.println("\t--CLUSTER " + j + "--\n");
										for (int i = 0; i < u; i++) {
											String qual = players.get(GREEDY_FirstDraw[j * u + i]).isnotQualified() ? ""
													: " (" + players.get(GREEDY_FirstDraw[j * u + i]).getEntry() + ") ";
											String seed = players.get(GREEDY_FirstDraw[j * u + i]).getHas_seed()
													? " (Seeded as "
															+ players.get(GREEDY_FirstDraw[j * u + i]).getSeed() + ") "
													: "";
											String unl = players.get(GREEDY_FirstDraw[j * u + i]).getUnlucky()
													? " (Unlucky) "
													: "";
											if (Verbose)
												System.out.println("\t " + (j * u + i + 1) + ".\t"
														+ players.get(GREEDY_FirstDraw[j * u + i]).getName() + qual
														+ seed + unl);
											if ((j * u + i + 1) % 2 == 1) {
												if (Verbose)
													System.out
															.println("\t/\n------- Penalty_type(s): "
																	+ h_desc[GREEDY_FirstDraw[j * u
																			+ i]][GREEDY_FirstDraw[j * u + i + 1]]
																	+ "\n\t\\");
											} else {
												if (Verbose)
													System.out.println("");
											}
										}
									}

									if (Verbose)
										System.out.println(
												"Computing conflicts measure and number for last generated 1st rounds.");
									nconf_unlucky_original = 0;
									for (int i = 0; i < (n / 2); i++) {

										// Add conflict 1st round to CPLEX_FirstDraw from CPLEX
										if (h[CPLEX_FirstDraw[i * 2]][CPLEX_FirstDraw[i * 2 + 1]] > 0)
											nconf_cplex++;
										cindex_cplex += h[CPLEX_FirstDraw[i * 2]][CPLEX_FirstDraw[i * 2 + 1]];

										// Add conflict 1st round to actual tournament draws
										if (h[i * 2][i * 2 + 1] > 0)
											nconf_original++;
										cindex_original += h[i * 2][i * 2 + 1];

										if ((players.get(i * 2).getUnlucky() && players.get(i * 2 + 1).getHas_seed())
												|| (players.get(i * 2 + 1).getUnlucky()
														&& players.get(i * 2).getHas_seed()))
											nconf_unlucky_original++;

										// Add conflict 1st round to greedy's draws
										if (h[GREEDY_FirstDraw[i * 2]][GREEDY_FirstDraw[i * 2 + 1]] > 0)
											nconf_greedy++;
										cindex_greedy += h[GREEDY_FirstDraw[i * 2]][GREEDY_FirstDraw[i * 2 + 1]];
										if (Verbose) {
											System.out.println("\t|CPLEX|\tMatch between "
													+ players.get(CPLEX_FirstDraw[i * 2]).getName() + " and "
													+ players.get(CPLEX_FirstDraw[i * 2 + 1]).getName() + " is h("
													+ CPLEX_FirstDraw[i * 2] + "," + CPLEX_FirstDraw[i * 2 + 1] + ")="
													+ h[CPLEX_FirstDraw[i * 2]][CPLEX_FirstDraw[i * 2 + 1]]);
											System.out.println("\t|GREEDY|\tMatch between "
													+ players.get(GREEDY_FirstDraw[i * 2]).getName() + " and "
													+ players.get(GREEDY_FirstDraw[i * 2 + 1]).getName() + " is h("
													+ GREEDY_FirstDraw[i * 2] + "," + GREEDY_FirstDraw[i * 2 + 1] + ")="
													+ h[GREEDY_FirstDraw[i * 2]][GREEDY_FirstDraw[i * 2 + 1]]);
											System.out.println(
													"\t|ORIGINAL|\tMatch Between " + players.get(i * 2).getName()
															+ " and " + players.get(i * 2 + 1).getName() + " is h("
															+ i * 2 + "," + (i * 2 + 1) + ")=" + h[i * 2][i * 2 + 1]);
											System.out.println("");
										}

									}
								}
								if (z == (s - 1)) {
									TimeElapsed();
									if (Verbose)
										System.out.println("Last whole tournament simulation");
									if (Verbose)
										System.out.println(
												"The following output is the last tourney generated from CPLEX's solution.");
								}

								// Reset structures
								for (int t = 0; t < (rounds); t++)
									for (int i = 0; i < n; i++) {
										CPLEX_Draws[i][t] = -1;
										GREEDY_Draws[i][t] = -1;
									}

								// Load the first round in each structure
								for (int i = 0; i < n; i++) {
									CPLEX_Draws[i][0] = CPLEX_FirstDraw[i];
									GREEDY_Draws[i][0] = GREEDY_FirstDraw[i];
								}
								for (int t = 0; t < (rounds); t++) {
									if (z == (s - 1))
										if (Verbose)
											System.out.println("Round R" + (int) Math.pow(2, (rounds - t - 1)));

									for (int i = 0; i < (Math.pow(2, (rounds - t - 1))); i++) {

										// Tournaments randomization for CPLEX
										// Code is a bit complex because of visual outputs
										// Refer to GREEDY randomization as to have the code without output
										if (Math.random() >= p[CPLEX_Draws[i * 2][t]][CPLEX_Draws[i * 2 + 1][t]]) {
											if ((t + 1) != rounds) {
												CPLEX_Draws[i][t + 1] = CPLEX_Draws[i * 2][t];
												if (z == (s - 1))
													if (Verbose)
														System.out.println("\t"
																+ players.get(CPLEX_Draws[i * 2][t]).getName()
																+ " wins over "
																+ players.get(CPLEX_Draws[i * 2 + 1][t]).getName());
											} else {
												if (z == (s - 1))
													if (Verbose)
														System.out.println("\t"
																+ players.get(CPLEX_Draws[i * 2][t]).getName()
																+ " wins over "
																+ players.get(CPLEX_Draws[i * 2 + 1][t]).getName());
											}
										} else {
											if ((t + 1) != rounds) {
												CPLEX_Draws[i][t + 1] = CPLEX_Draws[i * 2 + 1][t];
												if (z == (s - 1))
													if (Verbose)
														System.out.println(
																"\t" + players.get(CPLEX_Draws[i * 2 + 1][t]).getName()
																		+ " wins over "
																		+ players.get(CPLEX_Draws[i * 2][t]).getName());
											} else {
												if (z == (s - 1))
													if (Verbose)
														System.out.println(
																"\t" + players.get(CPLEX_Draws[i * 2 + 1][t]).getName()
																		+ " wins over "
																		+ players.get(CPLEX_Draws[i * 2][t]).getName());
											}
										}

										// Tournaments randomization for GREEDY
										if (Math.random() >= p[GREEDY_Draws[i * 2][t]][GREEDY_Draws[i * 2 + 1][t]]) {
											if ((t + 1) != rounds)
												GREEDY_Draws[i][t + 1] = GREEDY_Draws[i * 2][t];
										} else {
											if ((t + 1) != rounds)
												GREEDY_Draws[i][t + 1] = GREEDY_Draws[i * 2 + 1][t];

										}

									}
								}
								// Reset every iteration. Keep in the last one
								TOURNEY_cindex_greedy = TOURNEY_cindex_cplex = 0.0;
								TOURNEY_nconf_greedy = TOURNEY_nconf_cplex = 0;

								for (int t = 0; t < (rounds); t++) {
									for (int i = 0; i < (Math.pow(2, (rounds - t - 1))); i++) {
										if (z == 0) {
											if (h[ACTUAL_Rounds[i * 2][t]][ACTUAL_Rounds[i * 2 + 1][t]] > 0) {
												TOURNEY_nconf_original++;
												TOURNEY_cindex_original += h[ACTUAL_Rounds[i * 2][t]][ACTUAL_Rounds[i
														* 2 + 1][t]];
											}
										}
										if (h[GREEDY_Draws[i * 2][t]][GREEDY_Draws[i * 2 + 1][t]] > 0) {
											TOURNEY_nconf_greedy++;
											AVG_TOURNEY_greedy_nconf++;
											AVG_TOURNEY_greedy_cindex += h[GREEDY_Draws[i * 2][t]][GREEDY_Draws[i * 2
													+ 1][t]];
											TOURNEY_cindex_greedy += h[GREEDY_Draws[i * 2][t]][GREEDY_Draws[i * 2
													+ 1][t]];
										}
										if (h[CPLEX_Draws[i * 2][t]][CPLEX_Draws[i * 2 + 1][t]] > 0) {
											TOURNEY_nconf_cplex++;
											AVG_TOURNEY_cplex_nconf++;
											AVG_TOURNEY_cplex_cindex += h[CPLEX_Draws[i * 2][t]][CPLEX_Draws[i * 2
													+ 1][t]];
											TOURNEY_cindex_cplex += h[CPLEX_Draws[i * 2][t]][CPLEX_Draws[i * 2 + 1][t]];
										}

									}
								}
								CPLEX_Winners[z] = players.get(CPLEX_Draws[0][rounds - 1]).getName();
								GREEDY_Winners[z] = players.get(GREEDY_Draws[0][rounds - 1]).getName();
								if (z == (s - 1)) {
									if (Verbose)
										System.out.println("\nThe winner is: "
												+ players.get(CPLEX_Draws[0][rounds - 1]).getName() + "!");
								}
								if (Verbose)
									System.out.println("Iteration " + (z + 1) + " completed.");

							}
							AVG_cindex_cplex = AVG_cindex_cplex / s;
							AVG_nconf_cplex = AVG_nconf_cplex / s;
							AVG_cindex_greedy = AVG_cindex_greedy / s;
							AVG_nconf_greedy = AVG_nconf_greedy / s;
							AVG_unlucky_greedy = AVG_unlucky_greedy / s;
							AVG_unlucky_cplex = AVG_unlucky_cplex / s;

							AVG_TOURNEY_greedy_nconf = AVG_TOURNEY_greedy_nconf / s;
							AVG_TOURNEY_greedy_cindex = AVG_TOURNEY_greedy_cindex / s;
							AVG_TOURNEY_cplex_nconf = AVG_TOURNEY_cplex_nconf / s;
							AVG_TOURNEY_cplex_cindex = AVG_TOURNEY_cplex_cindex / s;

							TimeElapsed();

							String OF_improv_cplex = df.format((1 - (cplex.getObjValue() / objective_actual)) * 100);
							String OF_improv_greedy = df.format((1 - (objective_greedy / objective_actual)) * 100);

							String cindex_improv_cplex = df.format((1 - (cindex_cplex / cindex_original)) * 100);
							String AVG_cindex_improv_cplex = df
									.format((1 - (AVG_cindex_cplex / cindex_original)) * 100);
							String TOURNEY_cindex_cplex_improv = df
									.format((1 - (TOURNEY_cindex_cplex / TOURNEY_cindex_original)) * 100);
							String AVG_TOURNEY_cplex_cindex_improv = df
									.format((1 - (AVG_TOURNEY_cplex_cindex / TOURNEY_cindex_original)) * 100);

							String cindex_improv_greedy = df.format((1 - (cindex_greedy / cindex_original)) * 100);
							String AVG_cindex_improv_greedy = df
									.format((1 - (AVG_cindex_greedy / cindex_original)) * 100);
							String TOURNEY_cindex_greedy_improv = df
									.format((1 - (TOURNEY_cindex_greedy / TOURNEY_cindex_original)) * 100);
							String AVG_TOURNEY_greedy_cindex_improv = df
									.format((1 - (AVG_TOURNEY_greedy_cindex / TOURNEY_cindex_original)) * 100);

							System.out.println("Executive summary for " + TData.getTorunament_name() + " "
									+ TData.getTournament_year() + ":");
							System.out.println("\tTournament size (n): " + TData.getTournament_size());
							System.out.println("\tSeed size (m): " + TData.getSeeds() + " - Players per cluster (u): "
									+ u + " - Seeds per cluster (f): " + f + " - Unlucky per cluster (v/k): "
									+ (v / 4));
							System.out.println("\tLower bound for this problem is: " + LB);
							System.out.println("\tObjective function value from CPLEX: " + cplex.getObjValue());
							System.out.println("\tObjective function value from GREEDY: " + objective_greedy);
							System.out.println("\tSolutions status from CPLEX: " + cplex.getStatus());
							System.out.println("\tSolutions status from greedy: Feasible");
							System.out.println("\tTourney's original O.F. value: " + objective_actual);
							System.out.println("");
							if (Verbose) {
								System.out.println("ACTUAL\t\t|\t\tCPLEX\t\t\t|\t\t\tGREEDY");
								System.out.println("");
								System.out.println("--------------------");
								System.out.println("");

								System.out.println("Time");
								System.out.println("---\t\t|\t\t" + CPLEX_time + "\t\t|\t\t\t" + GR_time_first);
								System.out.println("O.F.");
								System.out.println(
										objective_actual + "\t\t|\t\t" + cplex.getObjValue() + "(" + OF_improv_cplex
												+ "%)\t\t|\t\t\t" + objective_greedy + "(" + OF_improv_greedy + "%)");
								System.out.println("O.F. with TL=Greedy_Time");
								System.out
										.println("---\t\t|\t\t" + CPLEX_sameTime + "\t\t\t|\t\t\t" + objective_greedy);
								System.out.println("Solution Status");
								System.out.println("----\t\t|\t\t" + cplex.getStatus() + "\t\t\t|\t\t\t----");

								System.out.println("");
								System.out.println("--------------------");
								System.out.println("");
								System.out.println("Average improvements over " + s + " simulations");
								System.out.println("\tNumber of conflicts 1st round");
								System.out.println(nconf_original + "\t\t|\t\t" + df.format(AVG_nconf_cplex)
										+ "\t\t\t|\t\t\t" + df.format(AVG_nconf_greedy));
								System.out.println("\tMeasure of conflicts 1st round");
								System.out.println(cindex_original + "\t\t|\t\t" + AVG_cindex_cplex + "("
										+ AVG_cindex_improv_cplex + "%)\t\t|\t\t\t" + AVG_cindex_greedy + "("
										+ AVG_cindex_improv_greedy + "%)");
								System.out.println("\tNumber of conflicts whole tourney");
								System.out.println(TOURNEY_nconf_original + "\t\t|\t\t" + AVG_TOURNEY_cplex_nconf
										+ "\t\t\t|\t\t\t" + AVG_TOURNEY_greedy_nconf);
								System.out.println("\tMeasure of conflicts whole tourney");
								System.out.println(TOURNEY_cindex_original + "\t\t|\t\t" + AVG_TOURNEY_cplex_cindex
										+ "(" + AVG_TOURNEY_cplex_cindex_improv + "%)\t\t|\t\t\t"
										+ AVG_TOURNEY_greedy_cindex + "(" + AVG_TOURNEY_greedy_cindex_improv + "%)");
								System.out.println("");
								System.out.println("--------------------");
								System.out.println("");
								System.out.println("Last simulation");
								System.out.println("\tNumber of conflicts 1st round");
								System.out.println(
										nconf_original + "\t\t|\t\t" + nconf_cplex + "\t\t\t|\t\t\t" + nconf_greedy);
								System.out.println("\tMeasure of conflicts 1st round");
								System.out.println(
										cindex_original + "\t\t|\t\t" + cindex_cplex + "(" + cindex_improv_cplex
												+ "%)\t\t|\t\t\t" + cindex_greedy + "(" + cindex_improv_greedy + "%)");
								System.out.println("\tNumber of conflicts whole tourney");
								System.out.println(TOURNEY_nconf_original + "\t\t|\t\t" + TOURNEY_nconf_cplex
										+ "\t\t\t|\t\t\t" + TOURNEY_nconf_greedy);
								System.out.println("\tMeasure of conflicts whole tourney");
								System.out.println(TOURNEY_cindex_original + "\t\t|\t\t" + TOURNEY_cindex_cplex + "("
										+ TOURNEY_cindex_cplex_improv + "%)\t\t|\t\t\t" + TOURNEY_cindex_greedy + "("
										+ TOURNEY_cindex_greedy_improv + "%)");
								System.out.println("\n\nTOURNAMENT SIMULATIONS WINNERS");
								System.out.println("CPLEX\t\t\t\tGREEDY");
								for (int i = 0; i < s; i++) {
									System.out.println(CPLEX_Winners[i] + "\t\t\t\t" + GREEDY_Winners[i]);
								}
							}

							// CSV OUTPUT
							BufferedWriter writerH = new BufferedWriter(new FileWriter(Path + "MatrixH.csv"));
							CSVPrinter csvPrinterH = new CSVPrinter(writerH,
									CSVFormat.DEFAULT.withHeader("Param", "Value"));
							csvPrinterH.printRecord("Size", n + "x" + n);
							csvPrinterH.printRecord("Max h", h_max);
							csvPrinterH.printRecord("Player with h=0", Q);
							csvPrinterH.printRecord("Average Degree", AVG_degree);
							csvPrinterH.printRecord("Max Degree", H_max_degree);
							csvPrinterH.printRecord("Average Weighted Degree", AVG_Wdegree);
							csvPrinterH.printRecord("Max Weighted Degree", H_max_Wdegree);
							csvPrinterH.flush();
							csvPrinterH.close();
							writerH.close();

							csvPrinterMain.printRecord(
									Type[Ty] + "-" + TData.getTorunament_name() + " " + TData.getTournament_year(), "", "",
									"", "", "", "", "", "", "");

							BufferedWriter writer = new BufferedWriter(new FileWriter(Path + "Results.csv"));
							CSVPrinter csvPrinter = new CSVPrinter(writer,
									CSVFormat.DEFAULT.withHeader("Name", "REAL", "CPLEX", "HEU"));
							// csvPrinter.printRecord("n=" + n + " m=v=" + v + " k=" + k, "", "", "");
							// total greedy iterations is GR_time
							csvPrinter.printRecord("Time", "-", df.format(CPLEX_time) + "s", df.format(GR_time_first));
							// csvPrinter.printRecord("LB with seeds", LB, LB, LB);
							csvPrinter.printRecord("O.F", objective_actual,
									cplex.getObjValue() + " (" + OF_improv_cplex + "\\%)",
									objective_greedy + " (" + OF_improv_greedy + "\\%)");
							String GreedyStatus = "Feasible";
							if (cplex.getStatus().equals("Optimal") && cplex.getObjValue() == objective_greedy)
								GreedyStatus = "Optimal";
							csvPrinter.printRecord("Solution status", "-", cplex.getStatus(), GreedyStatus);
							// swaps improv is delta_Sum_gr
							csvPrinter.printRecord("    O.F. with TL=GreedyTime", "-", CPLEX_sameTime,
									objective_greedy);
							csvPrinter.printRecord("Avg. Indexes | " + s + " simulations", "", "", "");
							csvPrinter.printRecord("    Unlucky pairings", nconf_unlucky_original,
									df.format(AVG_unlucky_cplex), df.format(AVG_unlucky_greedy));
							csvPrinter.printRecord("    Positive costs (1st round)", nconf_original,
									df.format(AVG_nconf_cplex), df.format(AVG_nconf_greedy));
							csvPrinter.printRecord("    Sum of costs (1st round)", cindex_original,
									df.format(AVG_cindex_cplex) + " (" + AVG_cindex_improv_cplex + "\\%)",
									df.format(AVG_cindex_greedy) + " (" + AVG_cindex_improv_greedy + "\\%)");
							csvPrinter.printRecord("    Positive costs (tournament)", TOURNEY_nconf_original,
									df.format(AVG_TOURNEY_cplex_nconf), df.format(AVG_TOURNEY_greedy_nconf));
							csvPrinter.printRecord("    Sum of costs (tournament)", TOURNEY_cindex_original,
									df.format(AVG_TOURNEY_cplex_cindex) + " (" + AVG_TOURNEY_cplex_cindex_improv
											+ "\\%)",
									df.format(AVG_TOURNEY_greedy_cindex) + " (" + AVG_TOURNEY_greedy_cindex_improv
											+ "\\%)");
							csvPrinter.printRecord("Unlucky information", "", "", "");
							csvPrinter.printRecord("    Played previous 4 Slams", misfortune_map.size(),
									misfortune_map.size(), misfortune_map.size());
							for (int i = 0; i < misfortune_count.length; i++)
								csvPrinter.printRecord("     Paired with a seed " + i + " times", misfortune_count[i],
										misfortune_count[i], misfortune_count[i]);

							csvPrinterMain.printRecord("REAL", "-", df.format(objective_actual), "Feasible", "-",
									df.format(nconf_unlucky_original), df.format(nconf_original),
									df.format(cindex_original), df.format(TOURNEY_nconf_original),
									df.format(TOURNEY_cindex_original));
							csvPrinterMain.printRecord("CPLEX", df.format(CPLEX_time),
									df.format(cplex.getObjValue()) + " (" + OF_improv_cplex + "%)", cplex.getStatus(),
									df.format(CPLEX_sameTime), df.format(AVG_unlucky_cplex), df.format(AVG_nconf_cplex),
									df.format(AVG_cindex_cplex) + " (" + AVG_cindex_improv_cplex + "%)",
									df.format(AVG_TOURNEY_cplex_nconf), df.format(AVG_TOURNEY_cplex_cindex) + " ("
											+ AVG_TOURNEY_cplex_cindex_improv + "%)");
							csvPrinterMain.printRecord("HEU", df.format(GR_time_first),
									df.format(objective_greedy) + " (" + OF_improv_greedy + "%)", GreedyStatus,
									df.format(objective_greedy), df.format(AVG_unlucky_greedy),
									df.format(AVG_nconf_greedy),
									df.format(AVG_cindex_greedy) + " (" + AVG_cindex_improv_greedy + "%)",
									df.format(AVG_TOURNEY_greedy_nconf), df.format(AVG_TOURNEY_greedy_cindex) + " ("
											+ AVG_TOURNEY_greedy_cindex_improv + "%)");
							csvPrinterMain.flush();

							/*
							 * csvPrinter.printRecord("Last simulation indexes", "", "", "");
							 * csvPrinter.printRecord("Number of conflicts in the 1st round",
							 * nconf_original, nconf_cplex, nconf_greedy);
							 * csvPrinter.printRecord("Measure of conflicts in the 1st round",
							 * cindex_original, df.format(cindex_cplex) + " (" + cindex_improv_cplex + "%)",
							 * df.format(cindex_greedy) + " (" + cindex_improv_greedy + "%)");
							 * 
							 * csvPrinter.printRecord("Number of conflicts in the tournament",
							 * TOURNEY_nconf_original, df.format(TOURNEY_nconf_cplex),
							 * df.format(TOURNEY_nconf_greedy));
							 * csvPrinter.printRecord("Measure of conflicts in the tournament",
							 * TOURNEY_cindex_original, df.format(TOURNEY_cindex_cplex) + " (" +
							 * TOURNEY_cindex_cplex_improv + "%)", df.format(TOURNEY_cindex_greedy) + " (" +
							 * TOURNEY_cindex_greedy_improv + "%)");
							 */

							csvPrinter.flush();
							csvPrinter.close();
							writer.close();
						} else {
							System.out.println("CPLEX is not able to find any solutions.");
						}
						TimeElapsed();
						cplex.end();

					} catch (

					IloException exc) {
						exc.printStackTrace();
					}

				}
			}
		}
		csvPrinterMain.close();
		System.out.println("Total time elapsed:" + (System.nanoTime() - Main) / 1000000000F + "s");
	}

}
