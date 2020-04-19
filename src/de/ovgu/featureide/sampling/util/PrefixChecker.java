package de.ovgu.featureide.sampling.util;

import java.util.List;

public class PrefixChecker {

	private static boolean allCharactersAreSame(String[] strings, int pos) {
		String first = strings[0];
		for (String curString : strings) {
			if (curString.length() <= pos || curString.charAt(pos) != first.charAt(pos)) {
				return false;
			}
		}
		return true;
	}

	public static String getLongestCommonPrefix(List<String> stringsList) {
		String[] strings = stringsList.toArray(new String[stringsList.size()]);
		int commonPrefixLength = 0;
		while (allCharactersAreSame(strings, commonPrefixLength)) {
			commonPrefixLength++;
		}
		return strings[0].substring(0, commonPrefixLength);
	}

	public static String getLongestCommonPrefix(String[] strings) {
		int commonPrefixLength = 0;
		while (allCharactersAreSame(strings, commonPrefixLength)) {
			commonPrefixLength++;
		}
		return strings[0].substring(0, commonPrefixLength);
	}
}
