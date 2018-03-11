import ilog.concert.*;
import ilog.cplex.*;

public class _Model {

	public static void main(String[] args) throws ErrorThrower {

		// Players
		int n = 4; // Amount of players in the tournament
		int m = 2; // Amount of seeded players
		// Clusters
		int k = 2; // Number of clusters

		// Integrity and validation tests on inputs
		if ((n % 2) != 0 || (m % 2) != 0 || ((n / k) % 2) != 0) {
			throw new ErrorThrower("n,m must be even, and mod(n/k)=0");
		}
		if (m >= (n - 1)) {
			throw new ErrorThrower("There are more seeds than players.");
		}

		// Generate auxiliary variables
		int u = n / k; // Amount of players per cluster
		int f = m / k; // Amount of seeded players per cluster
		Double h[][] = new Double[n][n]; // Matrix H

		// CPLEX Instance
		try {

			IloCplex cplex = new IloCplex();

			// Initialize the X_ij matrix with i=n (players) and j=k (clusters)
			IloIntVar[][] x = new IloIntVar[n][];
			for (int i = 0; i < n; i++) {
				x[i] = cplex.intVarArray(k, 0, 1);
			}

			// RANDOM GENERATION OF INPUTS
			// Randomly assign h-coefficients in the H MATRIX
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					if (i < j) {
						h[i][j] = Math.floor(Math.random() * 100.0) / 100.0;
						h[j][i] = h[i][j];
					}
					if (i == j) {
						h[i][j] = 0.0;
					}
				}
			}
			// Display coefficients in h
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					System.out.print(h[i][j] + "\t");
				}
				System.out.println();
			}

			// Assign seeded players
			// This allocation is only a generic example
			// Start from the X11 and then advance by going down by one row
			// Increase the amount of allocated player until they reach amount f in the
			// j-cluster
			// Iterate through clusters. top stores the first available player which can be
			// assigned in the next cluster
			int top = 0;
			for (int j = 0; j < k; j++) {
				for (int alfa = 0; alfa < f; alfa++) {
					x[top][j] = cplex.intVar(1, 1);
					top++;
				}
			}

			// Initialize expressions for constraints generation
			IloLinearNumExpr[] PlayersPerCluster = new IloLinearNumExpr[k];
			IloLinearNumExpr[] PlayerMaxAssignations = new IloLinearNumExpr[n];

			// Generate the expression : maximum players per cluster
			for (int j = 0; j < k; j++) {
				PlayersPerCluster[j] = cplex.linearNumExpr();
				for (int i = 0; i < n; i++) {
					PlayersPerCluster[j].addTerm(1.0, x[i][j]);
				}
			}

			// Generate expression : amount of clusters per player (1)
			for (int i = 0; i < n; i++) {
				PlayerMaxAssignations[i] = cplex.linearNumExpr();
				for (int j = 0; j < k; j++) {
					PlayerMaxAssignations[i].addTerm(1.0, x[i][j]);
				}
			}

			// Add constraints generated from expressions to the model
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
			// Add the O.F. to the model
			cplex.addMinimize(objective);
			cplex.exportModel("Test.lp");

			// Parameters for CPLEX
			cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
			cplex.setParam(IloCplex.Param.TimeLimit, 600);

			// Solve
			if (cplex.solve()) {
				System.out.println("Objective function value: " + cplex.getObjValue());
				System.out.println("Solutions status: " + cplex.getStatus());
				for (int i = 0; i < n; i++) {
					for (int j = 0; j < k; j++) {
						System.out.print("X(" + i + "," + j + ")=" + cplex.getValue(x[i][j]) + "\t");
					}
					System.out.println("");
				}

			} else {
				throw new ErrorThrower("No solution found");
			}

			cplex.end();
		} catch (IloException exc) {
			exc.printStackTrace();
		}
	}

}
