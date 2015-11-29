package server;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class TimedExit {
	Timer timer = new Timer();
	TimerTask exitProgram = new TimerTask() {
		public void run() {
			System.exit(0);
		}
	};
	
	public TimedExit() {
		timer.schedule(exitProgram, new Date(System.currentTimeMillis()+1000));
	}
}
