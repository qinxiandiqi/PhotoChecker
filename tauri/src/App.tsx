import { useState } from "react";
import { HomeScreen } from "./components/home";
import { AboutScreen } from "./components/about";
import { Button } from "daisyui";

type ViewType = "home" | "about";

function App() {
  const [currentView, setCurrentView] = useState<ViewType>("home");

  return (
    <div className="min-h-screen bg-base-200">
      {/* 导航栏 */}
      <header className="navbar bg-base-100 shadow-lg">
        <div className="navbar-start">
          <div className="flex items-center space-x-2">
            <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center">
              <span className="text-primary-content font-bold text-sm">PC</span>
            </div>
            <h1 className="text-xl font-bold">PhotoChecker</h1>
          </div>
        </div>

        <div className="navbar-end">
          <Button
            size="sm"
            variant={currentView === "home" ? "ghost" : "default"}
            onClick={() => setCurrentView("home")}
            className="mr-2"
          >
            主页
          </Button>
          <Button
            size="sm"
            variant={currentView === "about" ? "ghost" : "default"}
            onClick={() => setCurrentView("about")}
          >
            关于
          </Button>
        </div>
      </header>

      {/* 主要内容 */}
      <main className="flex-1">
        {currentView === "home" ? <HomeScreen /> : <AboutScreen />}
      </main>
    </div>
  );
}

export default App;