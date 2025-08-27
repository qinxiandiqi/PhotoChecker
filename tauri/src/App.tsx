import React, { useState, useCallback } from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { ThemeProvider, CssBaseline } from '@mui/material';
import { theme } from './theme';
import Home from './pages/Home';
import About from './pages/About';
import { HomeUIState, PhotoInfo } from './types/photo';
import { parseExif } from './utils/exifParser';

function App() {
  const [photoState, setPhotoState] = useState<HomeUIState>({
    type: 'empty',
  });

  const [showAbout, setShowAbout] = useState(false);

  const handlePhotoSelect = useCallback(async (file: File) => {
    setPhotoState({
      type: 'loading',
      photoInfo: {
        uri: URL.createObjectURL(file),
        readExifInfoList: [],
      },
    });

    try {
      const photoInfo = await parseExif(file);
      setPhotoState({
        type: 'success',
        photoInfo,
      });
    } catch (error) {
      setPhotoState({
        type: 'error',
        photoInfo: {
          uri: URL.createObjectURL(file),
          readExifInfoList: [],
        },
        error: error instanceof Error ? error.message : '解析照片时发生错误',
      });
    }
  }, []);

  const handleAboutClick = useCallback(() => {
    setShowAbout(true);
  }, []);

  const handleBackToHome = useCallback(() => {
    setShowAbout(false);
  }, []);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Router>
        <Routes>
          <Route 
            path="/" 
            element={
              <Home
                photoState={photoState}
                onPhotoSelect={handlePhotoSelect}
                onAboutClick={handleAboutClick}
              />
            } 
          />
          <Route 
            path="/about" 
            element={
              <About onBack={handleBackToHome} />
            } 
          />
        </Routes>
      </Router>
    </ThemeProvider>
  );
}

export default App;
