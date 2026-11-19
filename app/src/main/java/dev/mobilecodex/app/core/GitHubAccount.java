package dev.mobilecodex.app.core;
import java.util.List;
import java.util.regex.Pattern;
/** Fixed GitHub CLI device login. Tokens stay in the app-private Linux credential store. */
public final class GitHubAccount {
 private GitHubAccount(){}
 public static final String ENV="unset GH_TOKEN GITHUB_TOKEN GH_ENTERPRISE_TOKEN GITHUB_ENTERPRISE_TOKEN; export GH_CONFIG_DIR=/root/.config/gh GH_PROMPT_DISABLED=1 GH_BROWSER=/bin/true; ";
 public static String command(boolean login){
  String prepare=login?"if ! command -v gh >/dev/null 2>&1 || ! command -v git >/dev/null 2>&1; then\n if [ ! -s /etc/pacman.d/gnupg/pubring.gpg ]; then pacman-key --init; fi\n pacman-key --populate archlinuxarm\n pacman -Syyu --noconfirm --needed github-cli git ca-certificates\nfi\n":"command -v gh >/dev/null 2>&1\n";
  return "set -eu\numask 077\n"+ENV+"\n"+prepare+(login?"if ! gh auth status --hostname github.com >/dev/null 2>&1; then\n printf '\\n' | gh auth login --hostname github.com --git-protocol https --web\nfi\n":"gh auth status --hostname github.com >/dev/null 2>&1\n")+"gh auth setup-git --hostname github.com\naccount=$(gh api --hostname github.com user --jq .login)\nprintf 'MC_GITHUB_ACCOUNT=%s\\n' \"$account\"\n";
 }
 public static String account(int exit,String output){if(exit!=0)return "";var m=Pattern.compile("(?m)^MC_GITHUB_ACCOUNT=([A-Za-z0-9][A-Za-z0-9-]{0,38})\\r?$").matcher(output==null?"":output);String account="";int count=0;while(m.find()){account=m.group(1);count++;}return count==1?account:"";}
 public static String code(String output){var m=Pattern.compile("(?i)one-time code:\\s*([A-Z0-9]{4}-[A-Z0-9]{4})").matcher(output==null?"":output);return m.find()?m.group(1).toUpperCase(java.util.Locale.ROOT):"";}
 public static String helper(List<String> command){return "!"+String.join(" ",command.stream().map(DeveloperBuild::quote).toList());}
 public static String helperCommand(){return ENV+"exec gh auth git-credential \"$0\"";}
}
