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
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.math3.linear.*;

public class Main {
	public static long Clock;
	private static String tourney_id = "2017-580";
	private static int k = 4;
	private static Boolean generateH = false;
	private static Boolean Exact = true;
	private static int Heuristic = 4;
	private static Boolean Verbose = true;

	private static void TimeElapsed() {
		System.out.println("Time elapsed:" + (System.nanoTime() - Clock) / 1000000000F + "s");
	}

	private static Boolean isOne(Double var) {
		if (var > 0.9 && var < 1.1)
			return true;
		else
			return false;
	}

	private static int getID(ArrayList<Player> p, String name) {
		Boolean notfound = true;
		int ret = -1;
		for (int i = 0; i < p.size() && notfound; i++) {
			if (p.get(i).getName().equals(name)) {
				notfound = false;
				ret = i;
			}
		}
		return ret;
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

	@SuppressWarnings("static-access")
	public static void main(String[] args) throws ErrorThrower, SQLException, IOException, ParseException {

		// Auxiliary tools
		Clock = System.nanoTime();
		Random randomGenerator = new Random();
		Calendar now = Calendar.getInstance();
		DecimalFormat df = new DecimalFormat();
		df.setMaximumFractionDigits(2);
		String unique = now.get(Calendar.HOUR_OF_DAY) + "_" + now.get(Calendar.MINUTE) + "-"
				+ ThreadLocalRandom.current().nextInt(0, 100 + 1);

		// Loading tournament data into the app
		Connection conn;
		try {
			conn = DriverManager.getConnection("jdbc:sqlite:atpdatabase.db");
		} catch (SQLException e1) {
			throw new ErrorThrower("Can't connect to SQLite DB: " + e1.getMessage());
		}
		Statement statement = conn.createStatement();
		statement.setQueryTimeout(15);
		ResultSet res = statement.executeQuery(
				"SELECT m.tourney_name, m.tourney_date, m.tourney_id, m.match_num, m.draw_size, m.winner_name, m.winner_entry, m.winner_seedposition, m.winner_id, p1.country as winner_country, p1.hand as winner_hand, m.winner_seed, m.winner_rank, \n"
						+ "			m.loser_name,m.loser_seedposition, m.loser_entry, m.loser_id, p2.country as loser_country, p2.hand as loser_hand, m.loser_seed, m.loser_rank \n"
						+ "FROM matches AS m\n" + "JOIN player p1 ON p1.id = m.winner_id\n"
						+ "JOIN player p2 ON p2.id = m.loser_id\n" + "WHERE 	tourney_id='" + tourney_id
						+ "'  AND round='R128' ORDER BY match_num\n");

		int max_seed = 0;
		int tournament_size = 0;
		String torunament_name = "";
		int tournament_date = 0;

		ArrayList<Player> players = new ArrayList<Player>();
		while (res.next()) {
			if (max_seed == 0) {
				tournament_size = res.getInt("draw_size");
				tournament_date = res.getInt("tourney_date");
				torunament_name = res.getString("tourney_name");
			}
			if (res.getInt("winner_seed") > 0 || res.getInt("loser_seed") > 0) {
				max_seed++;
			}
			players.add(new Player(res.getInt("winner_id"), res.getString("winner_name"), res.getInt("winner_seed"),
					res.getInt("winner_seedposition"), res.getInt("winner_rank"), res.getString("winner_country"),
					res.getString("winner_entry"), res.getInt("match_num")));
			players.add(new Player(res.getInt("loser_id"), res.getString("loser_name"), res.getInt("loser_seed"),
					res.getInt("loser_seedposition"), res.getInt("loser_rank"), res.getString("loser_country"),
					res.getString("loser_entry"), res.getInt("match_num")));
		}
		try {
			File f = new File("out/" + torunament_name + "_" + unique + "/");
			f.mkdirs();
			FileOutputStream fout = new FileOutputStream(
					"out/" + torunament_name + "_" + unique + "/Instance_" + unique + ".txt");
			MultiOutputStream multiOut = new MultiOutputStream(System.out, fout);
			PrintStream stdout = new PrintStream(multiOut);

			System.setOut(stdout);
		} catch (FileNotFoundException ex) {
			throw new ErrorThrower("File for output not found.");
		}
		System.out.println("Dataset loaded for " + torunament_name);
		System.out.println("\tTournament size (n): " + tournament_size);
		System.out.println("\tSeed size (m): " + max_seed);

		System.out.println("0 for Heuristic - 1 for Exact");
		Scanner scanner = new Scanner(System.in);
		if (scanner.nextInt() == 1) {
			System.out.println("Exact mode");
			Exact = true;
			scanner.close();
		} else {
			Exact = false;
			System.out.println("1-2 for Heuristics");
			Heuristic = scanner.nextInt();
			Exact = false;
			scanner.close();

		}

		TimeElapsed();

		// TournamentAllocationProblem parameters
		int n = tournament_size;
		int m = max_seed;

		// Integrity and validation tests on inputs
		if ((n % 2) != 0 || (m % 2) != 0 || ((n / k) % 2) != 0) {
			throw new ErrorThrower("n,m must be even, and mod(n/k)=0");
		}
		if (m >= (n - 1)) {
			throw new ErrorThrower("Well... I bet you don't need a this tool :-)");
		}
		int u = n / k; // Amount of players per cluster
		int f = m / k; // Amount of seeds per cluster
		int s = 1; // Number of simulations performed for the whole tournament
		int rounds = (int) (Math.log(n) / Math.log(2));
		Double LB = -1.0; // Lower Bound for the problem
		Double h[][] = new Double[n][n]; // Matrix of h-coefficients
		String h_desc[][] = new String[n][n]; // Descriptions h conflicts' types

		int ACTUAL_FirstRound[][] = new int[n][k]; // |ACTUAL| Cluster in actual tournament
		int ACTUAL_Rounds[][] = new int[n][rounds]; // |ACTUAL| Tournament representation

		int CPLEX_FirstDraw[] = new int[n]; // |CPLEX| Draw for first round
		int CPLEX_Draws[][] = new int[n][rounds]; // |CPLEX| Simulations for other rounds

		int GreedySolution[][] = new int[n][k]; // |CONST| X_ij for greedy solution
		int GREEDY_FirstDraw[] = new int[n]; // |CONST| Draw for first round
		int GREEDY_Draws[][] = new int[n][rounds]; // |CONST| Simulations for other rounds

		// CPLEX Instance
		try {
			IloCplex cplex = new IloCplex();
			IloIntVar[][] x = new IloIntVar[n][];
			for (int i = 0; i < n; i++) {
				x[i] = cplex.intVarArray(k, 0, 1);
			}

			// Seeding players in their clusters
			System.out.println("Seeding players:");

			// Reset the greedy's solution
			for (int i = 0; i < n; i++)
				for (int j = 0; j < k; j++)
					GreedySolution[i][j] = -1;

			int seeds = 0;
			int count_cluster[] = new int[k];
			for (int i = 0; i < players.size(); i++) {
				for (int j = 0; j < k; j++) {
					if (players.get(i).getSeed() > 0) {

						// Seeding in the correct position
						if (players.get(i).getSeedposition() >= ((j * u) + 1)
								&& players.get(i).getSeedposition() <= ((j + 1) * u)
								&& players.get(i).getHas_seed() == false) {
							players.get(i).setHas_seed(true);
							x[i][j] = cplex.intVar(1, 1);
							GreedySolution[i][j] = 1;
							seeds++;
							count_cluster[j]++;
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
			for (int i = 0; i < k; i++) {
				System.out.print(i + ":" + count_cluster[i] + " ");
			}
			TimeElapsed();
			System.out.println("");

			// Generate and load the H-Matrix with its class
			MatrixH matH = new MatrixH(n, players, tourney_id, tournament_date);
			// Commented - generation of H matrix
			if (generateH == true) {
				matH.generate();
				matH.print();
				matH.print_desc();
			}
			h = matH.load();
			h_desc = matH.load_desc();

			// Load the actual tournament in order to later compare it to simulations
			for (int t = 0; t < (rounds); t++)
				for (int i = 0; i < n; i++)
					ACTUAL_Rounds[i][t] = -1;

			ResultSet res2 = statement.executeQuery("SELECT winner_name, loser_name\n"
					+ "FROM matches WHERE tourney_id='" + tourney_id + "' ORDER BY match_num;");
			int id_w = 0, id_l = 0, current_round = 0, rindex = 0, nrindex = 0;
			while (res2.next()) {
				id_w = getID(players, res.getString("winner_name"));
				id_l = getID(players, res.getString("loser_name"));
				ACTUAL_Rounds[rindex][current_round] = id_w;
				if ((current_round + 1) < rounds)
					ACTUAL_Rounds[nrindex][current_round + 1] = id_w;
				rindex++;
				nrindex++;
				ACTUAL_Rounds[rindex][current_round] = id_l;
				rindex++;
				if ((nrindex) == (int) Math.pow(2, (rounds - current_round - 1))) {
					current_round++;
					rindex = 0;
					nrindex = 0;
				}

			}
			conn.close();

			// Check the matrix is semi-definite positive
			// This will guarantee the existence for the optimal solution
			// Using MatrixUtils from Math3
			System.out.println("Checking properties of MatrixH:");
			RealMatrix Mx = MatrixUtils.createRealMatrix(n, n);
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					Mx.setEntry(i, j, (Double) h[i][j]);
				}
			}
			EigenDecomposition testPSD = new EigenDecomposition(Mx);
			System.out.println("\tHas complex eigen values: " + testPSD.hasComplexEigenvalues());
			if (testPSD.hasComplexEigenvalues() == true) {
				throw new ErrorThrower("MatrixH is not semi-definite positive. Problem is not convex");
			}
			double[] eval = testPSD.getRealEigenvalues();
			double max = 0, sum = 0.00, prod = 1.00;
			for (int i = 0; i < eval.length; i++) {
				if (i == 0) {
					max = eval[i];
				}
				if (eval[i] > max) {
					max = eval[i];
				}
			}
			for (int i = 0; i < eval.length; i++) {
				sum = sum + eval[i];
				prod = prod * eval[i];
			}
			System.out.println("\tLargest eigen-value: " + max);
			System.out.println("\tSum of all eigen-values (trace): " + sum);
			System.out.println("\tProduct of all eigen-values (determinant): " + prod);

			// Approximation tolerance for trace
			if (sum < -Math.pow(1, -3) || sum > Math.pow(1, -3)) {
				throw new ErrorThrower("MatrixH is not semi-definite positive.Problem is not convex");
			}

			TimeElapsed();
			System.out.println("");

			// Compute the lower bound with seeds
			System.out.println("Computing the lower bound.");
			for (int j = 0; j < k; j++) {
				if (Verbose)
					System.out.println("\tCLUSTER " + j + " | ------------------");
				for (int alfa = 0; alfa < n; alfa++) {
					for (int beta = 0; beta < n; beta++) {
						if (alfa > beta) {
							if (GreedySolution[alfa][j] > 0 && GreedySolution[beta][j] > 0) {
								LB += h[alfa][beta];
								if (Verbose && h[alfa][beta] > 0)
									System.out.println("\t" + players.get(alfa).getName() + " and "
											+ players.get(beta).getName() + ":+" + h[alfa][beta]);
							}
						}
					}
				}
			}
			System.out.println("Lower bound for this problem is: " + LB);

			// Compute the objective function for the actual draw
			// As to compare the delta with CPLEX and greedy

			Double objective_actual = 0.0;
			for (int j = 0; j < k; j++) {
				for (int alfa = 0; alfa < n; alfa++) {
					for (int beta = 0; beta < n; beta++) {
						if (alfa < beta) {
							objective_actual += ACTUAL_FirstRound[alfa][j] * ACTUAL_FirstRound[beta][j] * h[alfa][beta];
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
			for (int i = 0; i < n; i++) {
				degree = 0.0;
				weighted_degree = 0.0;
				for (int j = 0; j < n; j++) {
					weighted_degree += h[i][j];
					if (h[i][j] > 0)
						degree++;
				}
				h_Degree[i][i] = degree;
				players.get(i).setDegree(degree);
				degree_map_weighted.put(Integer.toString(i), weighted_degree);
				if (i == 0) {
					H_max_degree = degree;
					H_min_degree = degree;
				}
				if (degree > H_max_degree)
					H_max_degree = degree;
				if (degree < H_min_degree && degree != 0.0)
					H_min_degree = degree;
			}
			// Sorting the hash map
			Map<String, Double> degree_map_weighted_sorted = sortByValue(degree_map_weighted);

			System.out.println("Allocation is based on players's weighted degree:");

			int id = -1;

			Double current_obj = 0.0, current_min_obj = 0.0;
			int min_cluster = 0;
			int free_in_cluster[] = new int[k];
			Boolean assignation = false;
			Boolean first_cl = true;
			for (int j = 0; j < k; j++)
				free_in_cluster[j] = u - f;

			for (Map.Entry<String, Double> e : degree_map_weighted_sorted.entrySet()) {
				id = Integer.parseInt(e.getKey());
				if (!players.get(id).getHas_seed()) {
					first_cl = true;
					for (int j = 0; j < k; j++) {
						current_obj = 0.0;
						assignation = false;
						if (free_in_cluster[j] > 0) {
							assignation = true;
							for (int ii = 0; ii < n; ii++) {
								if (GreedySolution[ii][j] >= 0)
									current_obj += h[ii][id];
							}
							if (first_cl) {
								current_min_obj = current_obj + 1;
								first_cl = false;
							}
							if (current_obj < current_min_obj && assignation) {
								current_min_obj = current_obj;
								min_cluster = j;
							}

						}
					}
					if (Verbose)
						System.out.println("\tAssigning " + players.get(id).getName() + " to cluster " + min_cluster);
					GreedySolution[id][min_cluster] = 1;
					free_in_cluster[min_cluster]--;

				}

			}
			// Fill -1 values with zero.
			for (int i = 0; i < n; i++)
				for (int j = 0; j < k; j++)
					if (GreedySolution[i][j] == -1)
						GreedySolution[i][j] = 0;

			// Local search: swap 2 elements and compute an eventual positive O.F.
			// So, with Hamming distance of two try to find good swaps
			// Create a bucket for this swapping which excludes seeded players
			ArrayList<Integer> bucket_switch = new ArrayList<Integer>();
			for (int ii = 0; ii < n; ii++) {
				if (!players.get(ii).getHas_seed()) {
					bucket_switch.add(ii);
				}
			}
			System.out.println("\tBasic solution generated. Swapping");
			Double TL_greedy = 1.85;
			float greedy_start = System.nanoTime();
			float greedy_time = 0;
			int j_current_Alpha = -1, j_current_Beta = -1, index_Alpha = -1, index_Beta = -1, Alpha = -1, Beta = -1;
			Double delta_First = 0.0;
			Double delta_Second = 0.0;
			Double delta_Sum = 0.0;
			while (greedy_time < TL_greedy) {
				// INITIALIZE
				Beta = Alpha = index_Alpha = index_Beta = j_current_Beta = j_current_Alpha = -1;
				delta_First = delta_Second = 0.0;

				// Get two random elements
				// Iterate until they belong to different clusters
				while (j_current_Alpha == j_current_Beta) {
					index_Alpha = randomGenerator.nextInt(bucket_switch.size());
					Alpha = bucket_switch.get(index_Alpha);
					index_Beta = randomGenerator.nextInt(bucket_switch.size());
					Beta = bucket_switch.get(index_Beta);
					if (Alpha != Beta) {
						for (int j = 0; j < k; j++) {
							if (GreedySolution[Alpha][j] == 1)
								j_current_Alpha = j;
							if (GreedySolution[Beta][j] == 1)
								j_current_Beta = j;
						}
					}
				}
				// Once players are selected, try to swap them
				// In Beta's cluster
				for (int ii = 0; ii < n; ii++) {
					if (GreedySolution[ii][j_current_Beta] == 1) {
						// Player is in Beta's cluster
						// Compute delta
						if (ii != Beta)
							delta_First += h[Alpha][ii];
						delta_First -= h[Beta][ii];
					}
				}
				for (int ii = 0; ii < n; ii++) {
					if (GreedySolution[ii][j_current_Alpha] == 1) {
						// Player is in Beta's cluster
						// Compute delta
						if (ii != Alpha)
							delta_Second += h[Beta][ii];
						delta_Second -= h[Alpha][ii];
					}
				}
				if ((delta_First + delta_Second) < 0) {
					delta_Sum += delta_First + delta_Second;
					GreedySolution[Alpha][j_current_Alpha] = 0;
					GreedySolution[Alpha][j_current_Beta] = 1;
					GreedySolution[Beta][j_current_Beta] = 0;
					GreedySolution[Beta][j_current_Alpha] = 1;
					if (Verbose)
						System.out.println("Found a good swap between " + players.get(Alpha).getName() + " and "
								+ players.get(Beta).getName() + " with delta=" + (delta_First + delta_Second));
				}
				greedy_time = (System.nanoTime() - greedy_start) / 1000000000F;
			}
			System.out.println("Swaps executed in " + greedy_time + "s with a total delta of " + delta_Sum);
			float GR_time = (System.nanoTime() - GR_start) / 1000000000F;
			Double objective_greedy = 0.0;
			for (int j = 0; j < k; j++) {
				for (int alfa = 0; alfa < n; alfa++) {
					for (int beta = 0; beta < n; beta++) {
						if (GreedySolution[alfa][j] != -1 && GreedySolution[beta][j] != -1)
							if (alfa < beta) {
								objective_greedy += GreedySolution[alfa][j] * GreedySolution[beta][j] * h[alfa][beta];
							}
					}
				}
			}
			System.out.println("Greedy solution built in " + GR_time + "s has an O.F. of: " + objective_greedy);

			for (int j = 0; j < k; j++) {
				int count = 0;
				int count2 = 0;
				int deg = 0;
				int wdeg = 0;
				System.out.println("CLUSTER " + j + "------------------------");
				for (int i = 0; i < n; i++) {
					if (GreedySolution[i][j] == 1) {
						count2++;
						deg += h_Degree[i][i];
						wdeg += degree_map_weighted.get(Integer.toString(i));
						String qual = players.get(i).isnotQualified() ? " " : " (" + players.get(i).getEntry() + ") ";
						String seed = players.get(i).getHas_seed() ? " (Seeded as " + players.get(i).getSeed() + ") "
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
				System.out.println("Cluster " + j + " has a total of " + count2 + " players with " + count + " seeds.");
				System.out.println(
						"Avg degree:" + df.format(deg / count2) + " and wdegree:" + df.format(wdeg / count2) + "\n");
			}
			TimeElapsed();

			System.out.println("Using CPLEX to compute a solution.");
			// Initialise some expressions for constraints generation
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
			// Add the obj-function to the model
			cplex.addMinimize(objective);
			cplex.exportModel("out/" + torunament_name + "_" + unique + "/ModelInstance.lp");

			// Separate instance to test the solving time
			// Starting from the greedy solution

			Double CPLEX_sameTime = 0.0;

			if (Exact) {
				/*
				 * Double CPLEX_sameTime = 0.0; float CPLEX_greedy_time = 0; if (Exact) {
				 * System.out.
				 * println("Testing CPLEX with X_init=Greedy_sol in order to compare time elapsed."
				 * ); IloCplex cplex_greedy = new IloCplex(); cplex_greedy.importModel("out/" +
				 * torunament_name + "_" + unique + "/ModelInstance.lp");
				 * cplex_greedy.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
				 * cplex_greedy.setParam(IloCplex.Param.TimeLimit, 3600);
				 * cplex_greedy.setParam(IloCplex.Param.OptimalityTarget, 1);
				 * System.out.println("\tImporting greedy solution as the starting one.");
				 * IloNumVar[] c_startVar = new IloNumVar[n * k]; double[] c_startVal = new
				 * double[k * n]; for (int i = 0, idx = 0; i < n; ++i) { for (int j = 0; j < k;
				 * ++j) { c_startVar[idx] = x[i][j]; c_startVal[idx] = GreedySolution[i][j];
				 * ++idx; } } cplex_greedy.addMIPStart(c_startVar, c_startVal); float
				 * CPLEX_greedy_start = System.nanoTime(); if (cplex_greedy.solve()) {
				 * CPLEX_greedy_time = (System.nanoTime() - CPLEX_greedy_start) / 1000000000F;
				 * System.out.println("CPLEX starting from Greedy solution took: " +
				 * CPLEX_greedy_time + "s"); }
				 */

				System.out.println("Testing CPLEX with same time used by GREEDY.");
				IloCplex cplex_greedy_time = new IloCplex();
				cplex_greedy_time.importModel("out/" + torunament_name + "_" + unique + "/ModelInstance.lp");
				cplex_greedy_time.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
				cplex_greedy_time.setParam(IloCplex.Param.TimeLimit, GR_time);
				cplex_greedy_time.setParam(IloCplex.Param.OptimalityTarget, 1);
				if (cplex_greedy_time.solve()) {
					CPLEX_sameTime = cplex_greedy_time.getObjValue();
					System.out.println("CPLEX found in " + GR_time + " a solution with O.F." + CPLEX_sameTime);
				}

			}
			cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
			if (Verbose == false && !Exact)
				cplex.setParam(IloCplex.Param.MIP.Display, 0);

			// Heuristic or exact approach
			boolean solver = false;
			Double comparator_value = 0.0;
			float Heu_time = 0;
			float CPLEX_start = System.nanoTime();

			if (Exact) {
				cplex.setParam(IloCplex.Param.TimeLimit, 7200);
				cplex.setParam(IloCplex.Param.OptimalityTarget, 1);
				solver = cplex.solve();
			} else {
				// Matheuristic
				// Repetitions of heuristic in order to provide average results
				
				if (Heuristic == 1) {
					// Heuristic A
						float Heu_start = System.nanoTime();
						System.out.println("Using Heuristic A");
						int timelimit = 7;
						System.out.println("\tSolving for initial solutions to seed in the problem.");
						int solutions = 10;
						IloCplex cplex_sub1 = new IloCplex();
						cplex_sub1.importModel("out/" + torunament_name + "_" + unique + "/ModelInstance.lp");
						cplex_sub1.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
						cplex_sub1.setParam(IloCplex.Param.TimeLimit, timelimit);
						cplex_sub1.setParam(IloCplex.Param.MIP.Limits.Solutions, solutions);
						if (Verbose == false)
							cplex_sub1.setParam(IloCplex.Param.MIP.Display, 0);
						if (cplex_sub1.solve()) {
							System.out.println(cplex_sub1.getStatus());
							IloLPMatrix lp = (IloLPMatrix) cplex_sub1.LPMatrixIterator().next();
							double[] z1_temp = cplex_sub1.getValues(lp);
							int prebest = 0;
							double currentbest = 0;
							for (int i = 0; i < cplex_sub1.getSolnPoolNsolns(); i++) {
								if (i == 0) {
									currentbest = cplex_sub1.getObjValue(i) * 4;
								}
								if (currentbest > cplex_sub1.getObjValue(i)
										&& cplex_sub1.getObjValue(i) != cplex_sub1.getObjValue()) {
									prebest = i;
								}
							}
							double[] z2_temp = cplex_sub1.getValues(lp, prebest);

							// Convert variables with correct index (which is not sequential)
							String realIndex = "";
							int xi = 0;
							int xj = 0;
							Double[][] z1 = new Double[n][k];
							Double[][] z2 = new Double[n][k];
							for (int i = 0; i < (n * k); i++) {
								realIndex = lp.getNumVar(i).getName().replace("x", "");
								xi = Integer.parseInt(realIndex) - 1;
								xj = 0;
								while ((xi - n) >= 0) {
									xj++;
									xi = xi - n;
								}
								z1[xi][xj] = z1_temp[i];
								z2[xi][xj] = z2_temp[i];
							}
							System.out.println("\tBest O.F. value for fist iteration: " + cplex_sub1.getObjValue());
							@SuppressWarnings("unchecked")
							ArrayList<Integer>[] bucket_heuristic = (ArrayList<Integer>[]) new ArrayList[k];

							// Search for Q players which did not move in the last two solutions and fix
							// them in their clusters. Then add players (not Q) which did not move AND have
							// a
							// 0.45(Max_H_Degree) < degree < 0.55(Max_H_Degree) to a bucket
							// Extract a random 50% of variables from the bucket of each cluster and fix
							// them.
							IloNumVar[] startVar = new IloNumVar[n * k];
							double[] startVal = new double[k * n];
							for (int i = 0, idx = 0; i < n; ++i) {
								for (int j = 0; j < k; ++j) {
									startVar[idx] = x[i][j];
									startVal[idx] = z1[i][j];
									++idx;
								}
							}
							cplex.addMIPStart(startVar, startVal);
							int countQ=0;
							for (int i =0;i<players.size();i++) {
								if (!players.get(i).isnotQualified()) countQ++;
							}
							int fixed_Q[] = new int[k];
							int limit_Q = countQ/k;
							for (int j = 0; j < k; j++) {
								bucket_heuristic[j] = new ArrayList<Integer>();
								for (int i = 0; i < n; i++) {
									if (isOne(z1[i][j]) && isOne(z2[i][j]) && players.get(i).getHas_seed() == false) {
										if (players.get(i).isnotQualified() == false && fixed_Q[j]<limit_Q) {
											fixed_Q[j]++;
											System.out.println("\t\tFixing a stable Q identified in X(" + i + "," + j
													+ ")=" + (i + 1) * j + "=" + z1[i][j]);
											cplex.addEq(x[i][j], 1);
											z1[i][j] = 1.0;
											for (int jj = 0; jj < k; jj++) {
												if (jj != j) {
													cplex.addEq(x[i][jj], 0);
													z1[i][jj] = 0.0;
												}
											}
										} else {
											if (h_Degree[i][i] < H_max_degree * 0.55
													&& h_Degree[i][i] > H_max_degree * 0.45 && isOne(z1[i][j]))
												bucket_heuristic[j].add(i);
										}
									}
								}
							}

							int randomIndex = -1;
							int max_fixed = 0, randomVariable = 0;
							for (int j = 0; j < k; j++) {
								max_fixed = (int) Math.ceil(bucket_heuristic[j].size() * 0.8);
								for (int i = 0; i < max_fixed; i++) {
									randomIndex = randomGenerator.nextInt(bucket_heuristic[j].size());
									randomVariable = bucket_heuristic[j].get(randomIndex);
									System.out.println("\t\tFixing a random node identified in X(" + randomVariable
											+ "," + j + ")");
									cplex.addEq(x[randomVariable][j], 1);
									for (int jj = 0; jj < k; jj++) {
										if (jj != j)
											cplex.addEq(x[randomVariable][jj], 0);
									}
									bucket_heuristic[j].remove(randomIndex);
								}
							}

							cplex_sub1.end();
							cplex.setParam(IloCplex.Param.TimeLimit, timelimit * (3 / 2));
							solver = cplex.solve();
							float Heu_stop = (System.nanoTime() - Heu_start) / 1000000000F;
							System.out.println("Running comparator...");
							IloCplex cplex_comparator = new IloCplex();
							cplex_comparator.importModel("out/" + torunament_name + "_" + unique + "/ModelInstance.lp");
							cplex_comparator.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
							cplex_comparator.setParam(IloCplex.Param.TimeLimit, Heu_stop);
							if (Verbose == false)
								cplex_comparator.setParam(IloCplex.Param.MIP.Display, 0);
							cplex_comparator.solve();
							comparator_value = cplex_comparator.getObjValue();
							System.out.println(
									"Heuristic excecuted in " + Heu_stop + "s with O.F. of " + cplex.getObjValue());
							System.out.println("Comparator O.F.:" + comparator_value);
							Heu_time = Heu_stop;
						} else
							System.out.println("Error in sub problem");
					
				}

				if (Heuristic == 2) {

					// Weighted local branching
					float Heu_start = System.nanoTime();
					System.out.println("Using Heuristic B");
					System.out.println("Local branching with degrees");
					// Parameters for local branching
					float TL = 120;
					float TL_branch = 7;
					int k_branch = (int) Math.floor(n * 0.2);
					int k_branch_init = k_branch;
					float Heu_stop = 0;
					int counter = 0;
					int notimproving = 0;
					int notimproving_limit = 3;
					Double best_objective = -1.0;
					IloRange constraint = null;
					Boolean sw = true;
					cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
					cplex.setParam(IloCplex.Param.TimeLimit, TL_branch);
					cplex.setParam(IloCplex.Param.MIP.Display, 0);

					while (Heu_stop < TL) {
						counter++;
						System.out.println("Executing the iteration number " + counter + " with k_branch=" + k_branch);
						solver = cplex.solve();
						if (solver) {
							if (notimproving > notimproving_limit) {
								TL = -1;
							}
							if (counter == 1)
								best_objective = cplex.getObjValue() + 2;

							System.out.println("	Branch solution status is " + cplex.getStatus());
							System.out.println("\tO.F. value for this branch: " + cplex.getObjValue() + " vs best "
									+ best_objective);
							if (cplex.getStatus() == cplex.getStatus().Feasible
									|| cplex.getStatus() == cplex.getStatus().Optimal) {
								System.out.print(
										cplex.getStatus() + " " + cplex.getObjValue() + " " + cplex.getCplexStatus());

								if (cplex.getStatus() == cplex.getStatus().Optimal && constraint == null) {
									TL = -1;
									System.out.println("Optimal found!");
								}
								if (cplex.getObjValue() < best_objective) {
									notimproving = 0;
									cplex.writeSolutions("out/" + torunament_name + "_" + unique + "/Last_Sol.SOL");
									System.out.println("\tO.F. improvement detected. Storing variables");
									// Convert variables with correct index (which is not sequential)

									best_objective = cplex.getObjValue();
									IloLinearNumExpr LocalBranch = cplex.linearNumExpr();
									int active = 0;
									Double coeff = 0.0;
									Double terms = 0.0;
									for (int i = 0; i < n; i++) {
										for (int j = 0; j < k; j++) {
											if (sw) {

												if (h_Degree[i][i] != 0) {
													if (h_Degree[i][i] > H_max_degree * 0.35
															&& h_Degree[i][i] < H_max_degree * 0.65) {
														coeff = 1.1 * h_Degree[i][i];
														terms += 1.1 * h_Degree[i][i];
													} else {
														coeff = h_Degree[i][i];
														terms += h_Degree[i][i];
													}
												} else {
													coeff = 1.0;
													terms += 1;
												}
											} else {
												sw = true;
												terms = 1.0;
												coeff = 1.0;
											}
											if (!players.get(i).getHas_seed()) {
												if (isOne(cplex.getValue(x[i][j]))) {
													active++;
													LocalBranch.addTerm(-coeff, x[i][j]);
												} else {
													LocalBranch.addTerm(+coeff, x[i][j]);
												}
											}
										}
									}
									IloNumVar[] startVar = new IloNumVar[n * k];
									double[] startVal = new double[k * n];
									for (int i = 0, idx = 0; i < n; ++i) {
										for (int j = 0; j < k; ++j) {
											startVar[idx] = x[i][j];
											startVal[idx] = cplex.getValue(x[i][j]);
											++idx;
										}
									}
									cplex.addMIPStart(startVar, startVal);
									cplex.remove(constraint);
									constraint = cplex.addLe((k_branch - active) * terms, LocalBranch);
									if (k_branch_init > k_branch) {
										k_branch = k_branch * 3;
									}
									System.out.println("");
								} else {
									k_branch = k_branch * 3 / 2;
									sw = false;
									if (k_branch > n)
										k_branch = k_branch / 2;
									System.out.println("\tO.F. not improving. Going back");
									notimproving++;
								}
							} else {
								System.out.println("\tSub problem not feasible: " + cplex.getStatus());
								k_branch = k_branch * 3 / 2;
								if (k_branch > n)
									k_branch = k_branch / 2;
								sw = false;
								cplex.remove(constraint);
							}
						}
						Heu_stop = (System.nanoTime() - Heu_start) / 1000000000F;
					}
					cplex.readSolution("out/" + torunament_name + "_" + unique + "/Last_Sol.SOL");
					cplex.setParam(IloCplex.Param.TimeLimit, 0.1);
					solver = cplex.solve();
					Heu_stop = (System.nanoTime() - Heu_start) / 1000000000F;
					System.out.println("Running comparator...");
					IloCplex cplex_comparator = new IloCplex();
					cplex_comparator.importModel("out/" + torunament_name + "_" + unique + "/ModelInstance.lp");
					cplex_comparator.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
					cplex_comparator.setParam(IloCplex.Param.TimeLimit, Heu_stop);
					if (Verbose == false)
						cplex_comparator.setParam(IloCplex.Param.MIP.Display, 0);
					cplex_comparator.solve();
					comparator_value = cplex_comparator.getObjValue();
					System.out.println("Heuristic excecuted in " + Heu_stop + "s with O.F. of " + best_objective);
					System.out.println("Comparator O.F.:" + comparator_value);
					Heu_time = Heu_stop;
				}

			}

			// Solve
			float CPLEX_time = 0;
			if (solver) {
				if (Exact) {
					CPLEX_time = (System.nanoTime() - CPLEX_start) / 1000000000F;
				}
				System.out.println("CPLEX solution status: " + cplex.getStatus());
				cplex.writeSolutions("out/" + torunament_name + "_" + unique + "/Solution.SOL");
				File file = new File("out/" + torunament_name + "_" + unique + "/Last_Sol.SOL");
				file.delete();
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
					System.out.println(
							"Cluster " + j + " has a total of " + count2 + " players with " + count + " seeds.");
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

				Double AVG_cindex_greedy = 0.0;
				Double AVG_TOURNEY_greedy_cindex = 0.0;
				Double AVG_TOURNEY_greedy_nconf = 0.0;
				Double AVG_nconf_greedy = 0.0;

				// Indexes for last simulation
				Double cindex_original = 0.0;
				int nconf_original = 0;
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

				System.out.println("Generating draws for the first round both for CPLEX and greedy solution.");
				System.out.println("For each (1st round) draw generated, the whole tourney is simulated.");
				System.out.println("This process may take a while...");
				TimeElapsed();

				for (int z = 0; z < s; z++) {

					for (int i = 0; i < n; i++) {
						CPLEX_FirstDraw[i] = -1;
						GREEDY_FirstDraw[i] = -1;
					}

					@SuppressWarnings("unchecked")
					ArrayList<Integer>[] bucket = (ArrayList<Integer>[]) new ArrayList[k];
					@SuppressWarnings("unchecked")
					ArrayList<Integer>[] bucket_greedy = (ArrayList<Integer>[]) new ArrayList[k];
					for (int j = 0; j < k; j++) {
						count2 = 0;
						bucket[j] = new ArrayList<Integer>();
						bucket_greedy[j] = new ArrayList<Integer>();
						for (int i = 0; i < n; i++) {
							if (isOne(cplex.getValue(x[i][j]))) {
								if (players.get(i).getSeed() > 0) {
									CPLEX_FirstDraw[players.get(i).getSeedposition() - 1] = i;
									GREEDY_FirstDraw[players.get(i).getSeedposition() - 1] = i;
								} else {
									bucket[j].add(i);
									bucket_greedy[j].add(i);
								}
							}
						}
					}
					int randomIndex = -1, randomIndex_c = -1;
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

						AVG_cindex_greedy += h[GREEDY_FirstDraw[i * 2]][GREEDY_FirstDraw[i * 2 + 1]];
						if (h[GREEDY_FirstDraw[i * 2]][GREEDY_FirstDraw[i * 2 + 1]] > 0)
							AVG_nconf_greedy++;
					}
					if (z == (s - 1)) {
						System.out.println("");
						System.out.println("Last 1stR draw with the CPLEX's solution:");
						System.out.println(
								"Penalty types: 1 (first round) - 2 (second round) - 3 (third round) - L (Q and SF rounds) - C (country).");
						for (int j = 0; j < k; j++) {
							System.out.println("\t--CLUSTER " + j + "--\n");
							for (int i = 0; i < u; i++) {
								String qual = players.get(CPLEX_FirstDraw[j * u + i]).isnotQualified() ? ""
										: " (" + players.get(CPLEX_FirstDraw[j * u + i]).getEntry() + ") ";
								String seed = players.get(CPLEX_FirstDraw[j * u + i]).getHas_seed()
										? " (Seeded as " + players.get(CPLEX_FirstDraw[j * u + i]).getSeed() + ") "
										: "";
								System.out.println("\t " + (j * u + i + 1) + ".\t"
										+ players.get(CPLEX_FirstDraw[j * u + i]).getName() + qual + seed);
								if ((j * u + i + 1) % 2 == 1) {
									System.out.println("\t/\n------- Penalty_type(s): "
											+ h_desc[CPLEX_FirstDraw[j * u + i]][CPLEX_FirstDraw[j * u + i + 1]]
											+ "\n\t\\");
								} else {
									System.out.println("");
								}
							}
						}
						System.out.println("");
						System.out.println("");
						System.out.println("Last1 1stR draw with the solution found with greedy:");
						System.out.println(
								"Penalty types: 1 (first round) - 2 (second round) - 3 (third round) - L (Q and SF rounds) - C (country).");
						for (int j = 0; j < k; j++) {
							System.out.println("\t--CLUSTER " + j + "--\n");
							for (int i = 0; i < u; i++) {
								String qual = players.get(GREEDY_FirstDraw[j * u + i]).isnotQualified() ? ""
										: " (" + players.get(GREEDY_FirstDraw[j * u + i]).getEntry() + ") ";
								String seed = players.get(GREEDY_FirstDraw[j * u + i]).getHas_seed()
										? " (Seeded as " + players.get(GREEDY_FirstDraw[j * u + i]).getSeed() + ") "
										: "";
								System.out.println("\t " + (j * u + i + 1) + ".\t"
										+ players.get(CPLEX_FirstDraw[j * u + i]).getName() + qual + seed);
								if ((j * u + i + 1) % 2 == 1) {
									System.out.println("\t/\n------- Penalty_type(s): "
											+ h_desc[GREEDY_FirstDraw[j * u + i]][GREEDY_FirstDraw[j * u + i + 1]]
											+ "\n\t\\");
								} else {
									System.out.println("");
								}
							}
						}

						System.out.println("Computing conflicts measure and number for last generated 1st rounds.");
						for (int i = 0; i < (n / 2); i++) {

							// Add conflict 1st round to CPLEX_FirstDraw from CPLEX
							if (h[CPLEX_FirstDraw[i * 2]][CPLEX_FirstDraw[i * 2 + 1]] > 0)
								nconf_cplex++;
							cindex_cplex += h[CPLEX_FirstDraw[i * 2]][CPLEX_FirstDraw[i * 2 + 1]];

							// Add conflict 1st round to actual tournament draws
							if (h[i * 2][i * 2 + 1] > 0)
								nconf_original++;
							cindex_original += h[i * 2][i * 2 + 1];

							// Add conflict 1st round to greedy's draws
							if (h[GREEDY_FirstDraw[i * 2]][GREEDY_FirstDraw[i * 2 + 1]] > 0)
								nconf_greedy++;
							cindex_greedy += h[GREEDY_FirstDraw[i * 2]][GREEDY_FirstDraw[i * 2 + 1]];

							System.out.println(
									"\t|SOLUTION|\tMatch between " + players.get(CPLEX_FirstDraw[i * 2]).getName()
											+ " and " + players.get(CPLEX_FirstDraw[i * 2 + 1]).getName() + " is h("
											+ CPLEX_FirstDraw[i * 2] + "," + CPLEX_FirstDraw[i * 2 + 1] + ")="
											+ h[CPLEX_FirstDraw[i * 2]][CPLEX_FirstDraw[i * 2 + 1]]);
							System.out.println(
									"\t|CONSTRUCT|\tMatch between " + players.get(GREEDY_FirstDraw[i * 2]).getName()
											+ " and " + players.get(GREEDY_FirstDraw[i * 2 + 1]).getName() + " is h("
											+ GREEDY_FirstDraw[i * 2] + "," + GREEDY_FirstDraw[i * 2 + 1] + ")="
											+ h[GREEDY_FirstDraw[i * 2]][GREEDY_FirstDraw[i * 2 + 1]]);
							System.out.println("\t|ORIGINAL|\tMatch Between " + players.get(i * 2).getName() + " and "
									+ players.get(i * 2 + 1).getName() + " is h(" + i * 2 + "," + (i * 2 + 1) + ")="
									+ h[i * 2][i * 2 + 1]);
							System.out.println("");

						}
					}
					if (z == (s - 1)) {
						TimeElapsed();
						System.out.println("Last whole tournament simulation");
						System.out.println("The following output is the last tourney generated from CPLEX's solution.");
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
							System.out.println("Round R" + (int) Math.pow(2, (rounds - t - 1)));

						for (int i = 0; i < (Math.pow(2, (rounds - t - 1))); i++) {

							// Tournaments randomization for CPLEX
							// Code is a bit complex because of visual outputs
							// Refer to GREEDY randomization as to have the code without output
							if (Math.random() >= matH.P_win(CPLEX_Draws[i * 2][t], CPLEX_Draws[i * 2 + 1][t])) {
								if ((t + 1) != rounds) {
									CPLEX_Draws[i][t + 1] = CPLEX_Draws[i * 2][t];
									if (z == (s - 1))
										System.out.println("\t" + players.get(CPLEX_Draws[i * 2][t]).getName()
												+ " wins over " + players.get(CPLEX_Draws[i * 2 + 1][t]).getName());
								} else {
									if (z == (s - 1))
										System.out.println("\t" + players.get(CPLEX_Draws[i * 2][t]).getName()
												+ " wins over " + players.get(CPLEX_Draws[i * 2 + 1][t]).getName());
								}
							} else {
								if ((t + 1) != rounds) {
									CPLEX_Draws[i][t + 1] = CPLEX_Draws[i * 2 + 1][t];
									if (z == (s - 1))
										System.out.println("\t" + players.get(CPLEX_Draws[i * 2 + 1][t]).getName()
												+ " wins over " + players.get(CPLEX_Draws[i * 2][t]).getName());
								} else {
									if (z == (s - 1))
										System.out.println("\t" + players.get(CPLEX_Draws[i * 2 + 1][t]).getName()
												+ " wins over " + players.get(CPLEX_Draws[i * 2][t]).getName());
								}
							}

							// Tournaments randomization for GREEDY
							if (Math.random() >= matH.P_win(GREEDY_Draws[i * 2][t], GREEDY_Draws[i * 2 + 1][t])) {
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
									TOURNEY_cindex_original += h[ACTUAL_Rounds[i * 2][t]][ACTUAL_Rounds[i * 2 + 1][t]];
								}
							}
							if (h[GREEDY_Draws[i * 2][t]][GREEDY_Draws[i * 2 + 1][t]] > 0) {
								TOURNEY_nconf_greedy++;
								AVG_TOURNEY_greedy_nconf++;
								AVG_TOURNEY_greedy_cindex += h[GREEDY_Draws[i * 2][t]][GREEDY_Draws[i * 2 + 1][t]];
								TOURNEY_cindex_greedy += h[GREEDY_Draws[i * 2][t]][GREEDY_Draws[i * 2 + 1][t]];
							}
							if (h[CPLEX_Draws[i * 2][t]][CPLEX_Draws[i * 2 + 1][t]] > 0) {
								TOURNEY_nconf_cplex++;
								AVG_TOURNEY_cplex_nconf++;
								AVG_TOURNEY_cplex_cindex += h[CPLEX_Draws[i * 2][t]][CPLEX_Draws[i * 2 + 1][t]];
								TOURNEY_cindex_cplex += h[CPLEX_Draws[i * 2][t]][CPLEX_Draws[i * 2 + 1][t]];
							}

						}
					}
					CPLEX_Winners[z] = players.get(CPLEX_Draws[0][rounds - 1]).getName();
					GREEDY_Winners[z] = players.get(GREEDY_Draws[0][rounds - 1]).getName();
					if (z == (s - 1))
						System.out
								.println("\nThe winner is: " + players.get(CPLEX_Draws[0][rounds - 1]).getName() + "!");
					System.out.println("Iteration " + (z + 1) + " completed.");

				}
				AVG_cindex_cplex = AVG_cindex_cplex / s;
				AVG_nconf_cplex = AVG_nconf_cplex / s;
				AVG_cindex_greedy = AVG_cindex_greedy / s;
				AVG_nconf_greedy = AVG_nconf_greedy / s;

				AVG_TOURNEY_greedy_nconf = AVG_TOURNEY_greedy_nconf / s;
				AVG_TOURNEY_greedy_cindex = AVG_TOURNEY_greedy_cindex / s;
				AVG_TOURNEY_cplex_nconf = AVG_TOURNEY_cplex_nconf / s;
				AVG_TOURNEY_cplex_cindex = AVG_TOURNEY_cplex_cindex / s;

				TimeElapsed();

				String OF_improv_cplex = df.format((1 - (cplex.getObjValue() / objective_actual)) * 100);
				String OF_improv_greedy = df.format((1 - (objective_greedy / objective_actual)) * 100);

				String cindex_improv_cplex = df.format((1 - (cindex_cplex / cindex_original)) * 100);
				String AVG_cindex_improv_cplex = df.format((1 - (AVG_cindex_cplex / cindex_original)) * 100);
				String TOURNEY_cindex_cplex_improv = df
						.format((1 - (TOURNEY_cindex_cplex / TOURNEY_cindex_original)) * 100);
				String AVG_TOURNEY_cplex_cindex_improv = df
						.format((1 - (AVG_TOURNEY_cplex_cindex / TOURNEY_cindex_original)) * 100);

				String cindex_improv_greedy = df.format((1 - (cindex_greedy / cindex_original)) * 100);
				String AVG_cindex_improv_greedy = df.format((1 - (AVG_cindex_greedy / cindex_original)) * 100);
				String TOURNEY_cindex_greedy_improv = df
						.format((1 - (TOURNEY_cindex_greedy / TOURNEY_cindex_original)) * 100);
				String AVG_TOURNEY_greedy_cindex_improv = df
						.format((1 - (AVG_TOURNEY_greedy_cindex / TOURNEY_cindex_original)) * 100);

				System.out.println("Executive summary for " + torunament_name + ":");
				System.out.println("\tTournament size (n): " + tournament_size);
				System.out.println("\tSeed size (m): " + max_seed + " - Players per cluster (u): " + u
						+ " - Seeds per cluster (f): " + f);
				System.out.println("\tLower bound for this problem is: " + LB);
				System.out.println("\tObjective function value from CPLEX: " + cplex.getObjValue());
				System.out.println("\tObjective function value from GREEDY: " + objective_greedy);
				System.out.println("\tSolutions status from CPLEX: " + cplex.getStatus());
				System.out.println("\tSolutions status from greedy: Feasible");
				System.out.println("\tTourney's original O.F. value: " + objective_actual);
				System.out.println("");

				System.out.println("ACTUAL\t\t|\t\tCPLEX\t\t\t|\t\t\tGREEDY");
				System.out.println("");
				System.out.println("--------------------");
				System.out.println("");

				System.out.println("Time");
				System.out.println("---\t\t|\t\t" + CPLEX_time + "\t\t|\t\t\t" + GR_time);
				System.out.println("O.F.");
				System.out.println(objective_actual + "\t\t|\t\t" + cplex.getObjValue() + "(" + OF_improv_cplex
						+ "%)\t\t|\t\t\t" + objective_greedy + "(" + OF_improv_greedy + "%)");
				System.out.println("O.F. with TL=Greedy_Time");
				System.out.println("---\t\t|\t\t" + CPLEX_sameTime + "\t\t\t|\t\t\t" + objective_greedy);
				System.out.println("Solution Status");
				System.out.println("----\t\t|\t\t" + cplex.getStatus() + "\t\t\t|\t\t\t----");

				System.out.println("");
				System.out.println("--------------------");
				System.out.println("");
				System.out.println("Average improvements over " + s + " simulations");
				System.out.println("\tNumber of conflicts 1st round");
				System.out.println(nconf_original + "\t\t|\t\t" + df.format(AVG_nconf_cplex) + "\t\t\t|\t\t\t"
						+ df.format(AVG_nconf_greedy));
				System.out.println("\tMeasure of conflicts 1st round");
				System.out.println(cindex_original + "\t\t|\t\t" + AVG_cindex_cplex + "(" + AVG_cindex_improv_cplex
						+ "%)\t\t|\t\t\t" + AVG_cindex_greedy + "(" + AVG_cindex_improv_greedy + "%)");
				System.out.println("\tNumber of conflicts whole tourney");
				System.out.println(TOURNEY_nconf_original + "\t\t|\t\t" + AVG_TOURNEY_cplex_nconf + "\t\t\t|\t\t\t"
						+ AVG_TOURNEY_greedy_nconf);
				System.out.println("\tMeasure of conflicts whole tourney");
				System.out.println(TOURNEY_cindex_original + "\t\t|\t\t" + AVG_TOURNEY_cplex_cindex + "("
						+ AVG_TOURNEY_cplex_cindex_improv + "%)\t\t|\t\t\t" + AVG_TOURNEY_greedy_cindex + "("
						+ AVG_TOURNEY_greedy_cindex_improv + "%)");
				System.out.println("");
				System.out.println("--------------------");
				System.out.println("");
				System.out.println("Last simulation");
				System.out.println("\tNumber of conflicts 1st round");
				System.out.println(nconf_original + "\t\t|\t\t" + nconf_cplex + "\t\t\t|\t\t\t" + nconf_greedy);
				System.out.println("\tMeasure of conflicts 1st round");
				System.out.println(cindex_original + "\t\t|\t\t" + cindex_cplex + "(" + cindex_improv_cplex
						+ "%)\t\t|\t\t\t" + cindex_greedy + "(" + cindex_improv_greedy + "%)");
				System.out.println("\tNumber of conflicts whole tourney");
				System.out.println(TOURNEY_nconf_original + "\t\t|\t\t" + TOURNEY_nconf_cplex + "\t\t\t|\t\t\t"
						+ TOURNEY_nconf_greedy);
				System.out.println("\tMeasure of conflicts whole tourney");
				System.out.println(
						TOURNEY_cindex_original + "\t\t|\t\t" + TOURNEY_cindex_cplex + "(" + TOURNEY_cindex_cplex_improv
								+ "%)\t\t|\t\t\t" + TOURNEY_cindex_greedy + "(" + TOURNEY_cindex_greedy_improv + "%)");
				System.out.println("\n\nTOURNAMENT SIMULATIONS WINNERS");
				System.out.println("CPLEX\t\t\t\tGREEDY");
				for (int i = 0; i < s; i++) {
					System.out.println(CPLEX_Winners[i] + "\t\t\t\t" + GREEDY_Winners[i]);
				}
				if (!Exact) {
					System.out.println("");
					System.out.println("-----HEURISTIC INFORMATION");
					System.out.println("Heuristic excecuted in " + Heu_time + "s with O.F. of " + cplex.getObjValue());
					System.out
							.println("Standard CPLEX excecuted in " + Heu_time + "s with O.F. of " + comparator_value);
					System.out.println("The heuristic performed " + df.format(cplex.getObjValue() / comparator_value)
							+ "% better (worse) than pure CPLEX.");
				}

				// CSV OUTPUT
				BufferedWriter writer = new BufferedWriter(
						new FileWriter("out/" + torunament_name + "_" + unique + "/RES_Summary.csv"));

				CSVPrinter csvPrinter = new CSVPrinter(writer,
						CSVFormat.DEFAULT.withHeader("", "ACT", "CPLEX", "GREEDY"));
				csvPrinter.printRecord("LB", LB, LB, LB);
				csvPrinter.printRecord("Time", "-", df.format(CPLEX_time) + "s", df.format(GR_time) + "s");
				csvPrinter.printRecord("    CPLEX O.F. with TL=GreedyTime", "--", CPLEX_sameTime, objective_greedy);
				csvPrinter.printRecord("O.F.", objective_actual, cplex.getObjValue() + " (" + OF_improv_cplex + "%)",
						objective_greedy + " (" + OF_improv_greedy + "%)");
				csvPrinter.printRecord("Greedy improvement with swaps", "-", "-", -delta_Sum);
				csvPrinter.printRecord("Solution status", "-", cplex.getStatus(), "Feasible");
				csvPrinter.printRecord("Average indexes | s=" + s, "", "", "");
				csvPrinter.printRecord("Number of conflicts in the 1st round", nconf_original,
						df.format(AVG_nconf_cplex), df.format(AVG_nconf_greedy));
				csvPrinter.printRecord("Measure of conflicts in the 1st round", cindex_original,
						df.format(AVG_cindex_cplex) + " (" + AVG_cindex_improv_cplex + "%)",
						df.format(AVG_cindex_greedy) + " (" + AVG_cindex_improv_greedy + "%)");
				csvPrinter.printRecord("Number of conflicts in the tournament", TOURNEY_nconf_original,
						df.format(AVG_TOURNEY_cplex_nconf), df.format(AVG_TOURNEY_greedy_nconf));
				csvPrinter.printRecord("Measure of conflicts in the tournament", TOURNEY_cindex_original,
						df.format(AVG_TOURNEY_cplex_cindex) + " (" + AVG_TOURNEY_cplex_cindex_improv + "%)",
						df.format(AVG_TOURNEY_greedy_cindex) + " (" + AVG_TOURNEY_greedy_cindex_improv + "%)");
				csvPrinter.printRecord("Last simulation indexes", "", "", "");
				csvPrinter.printRecord("Number of conflicts in the 1st round", nconf_original, nconf_cplex,
						nconf_greedy);
				csvPrinter.printRecord("Measure of conflicts in the 1st round", cindex_original,
						df.format(cindex_cplex) + " (" + cindex_improv_cplex + "%)",
						df.format(cindex_greedy) + " (" + cindex_improv_greedy + "%)");

				csvPrinter.printRecord("Number of conflicts in the tournament", TOURNEY_nconf_original,
						df.format(TOURNEY_nconf_cplex), df.format(TOURNEY_nconf_greedy));
				csvPrinter.printRecord("Measure of conflicts in the tournament", TOURNEY_cindex_original,
						df.format(TOURNEY_cindex_cplex) + " (" + TOURNEY_cindex_cplex_improv + "%)",
						df.format(TOURNEY_cindex_greedy) + " (" + TOURNEY_cindex_greedy_improv + "%)");

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
