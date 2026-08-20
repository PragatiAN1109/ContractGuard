import { Route, Routes } from 'react-router-dom';
import { ProjectsPage } from './pages/ProjectsPage';
import { ProjectPage } from './pages/ProjectPage';
import { AnalysisPage } from './pages/AnalysisPage';

export function App() {
  return (
    <main className="app">
      <Routes>
        <Route path="/" element={<ProjectsPage />} />
        <Route path="/projects/:projectId" element={<ProjectPage />} />
        <Route path="/analyses/:analysisId" element={<AnalysisPage />} />
      </Routes>
    </main>
  );
}
