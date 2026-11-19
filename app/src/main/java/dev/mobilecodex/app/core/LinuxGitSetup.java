package dev.mobilecodex.app.core;
/** Explicit first-setup action only. Existing working Git never triggers a download. */
public final class LinuxGitSetup {
 private LinuxGitSetup(){}
 public static String command(){return "set -eu\nif ! command -v git >/dev/null 2>&1 || ! git --version; then\n  if [ ! -s /etc/pacman.d/gnupg/pubring.gpg ]; then pacman-key --init; fi\n  pacman-key --populate archlinuxarm\n  pacman -Syyu --noconfirm --needed git ca-certificates\nfi\ngit --version\necho MC_LINUX_GIT=passed\n";}
 public static boolean passed(int exit,String output){if(exit!=0)return false;var matcher=java.util.regex.Pattern.compile("(?m)^MC_LINUX_GIT=passed\\r?$").matcher(output==null?"":output);int count=0;while(matcher.find())count++;return count==1;}
}
