public class Player {
	private String name;
	private int id;
	private int seed;
	private int ranking;
	private int seedposition;
	private int match_num;
	private String entry;
	private Boolean has_seed;
	private String country;
	private int cluster;
	private Double degree;

	public Player(int id, String name, int seed, int seedposit, int ranking, String country, String entry, int mnum) {
		super();
		this.setName(name);
		this.setSeed(seed);
		this.setRanking(ranking);
		this.setCountry(country);
		this.setHas_seed(false);
		this.setSeedposition(seedposit);
		this.setId(id);
		this.setEntry(entry);
		this.setMatch_num(mnum);
	}

	public String getName() {
		return name;
	}

	public Boolean isnotQualified() {
		if (this.getEntry().equals("Q") || this.getEntry().equals("LL")) {
			return false;
		} else {
			return true;
		}
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getRanking() {
		return ranking;
	}

	public void setRanking(int ranking) {
		this.ranking = ranking;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public int getSeed() {
		return seed;
	}

	public void setSeed(int seed) {
		this.seed = seed;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getSeedposition() {
		return seedposition;
	}

	public void setSeedposition(int seedposition) {
		this.seedposition = seedposition;
	}

	public Boolean getHas_seed() {
		return has_seed;
	}

	public void setHas_seed(Boolean has_seed) {
		this.has_seed = has_seed;
	}

	public String getEntry() {
		return entry;
	}

	public void setEntry(String entry) {
		this.entry = entry;
	}

	public int getMatch_num() {
		return match_num;
	}

	public void setMatch_num(int match_num) {
		this.match_num = match_num;
	}

	public int getCluster() {
		return cluster;
	}

	public void setCluster(int cluster) {
		this.cluster = cluster;
	}

	public Double getDegree() {
		return degree;
	}

	public void setDegree(Double degree) {
		this.degree = degree;
	}

}
