#include <MAUtil/Moblet.h>

using namespace MAUtil;

class MyMoblet : public Moblet {
public:
	MyMoblet() {
		// todo: initialize
	}

	void keyPressEvent(int keyCode) {
		// todo: handle key presses
	}

	void keyReleaseEvent(int keyCode) {
		// todo: handle key releases
	}
};

extern "C" int MAMain() {
	Moblet::run(new MyMoblet());
	return 0;
};
