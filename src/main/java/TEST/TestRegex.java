package TEST;

public class TestRegex {

	public static void main(String[] args) {
		String result = "₱ is () a <> 12m !@#$%%^^&*( ++ good's güy";
		//result = result.replaceAll("[^\\w]", " ").replaceAll("\\s+"," ").trim();
		result = result.replaceAll("[^a-zA-Z0-9 ]", " ").replaceAll("\\s+"," ").trim();
		System.err.println(result);

	}

}
