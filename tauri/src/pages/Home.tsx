import React from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Container,
  Grid,
  Typography,
  Alert,
  Fab,
  IconButton,
  AppBar,
  Toolbar,
} from '@mui/material';
import {
  PhotoCamera,
  Info as InfoIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { theme } from '../theme';
import { usePhotoSelector, useImageDimensions } from '../hooks/usePhotoSelector';
import { HomeUIState } from '../types/photo';

interface HomeProps {
  photoState: HomeUIState;
  onPhotoSelect: (file: File) => void;
  onAboutClick: () => void;
  onRefresh?: () => void;
}

const Home: React.FC<HomeProps> = ({
  photoState,
  onPhotoSelect,
  onAboutClick,
  onRefresh,
}) => {
  const navigate = useNavigate();
  const { triggerFileInput } = usePhotoSelector();
  const { calculateResponsiveDimensions } = useImageDimensions();

  const renderContent = () => {
    switch (photoState.type) {
      case 'empty':
        return (
          <Box
            sx={{
              border: '2px dashed',
              borderColor: 'divider',
              borderRadius: 2,
              p: 4,
              textAlign: 'center',
              cursor: 'pointer',
              transition: 'all 0.3s ease',
              '&:hover': {
                borderColor: 'primary.main',
                bgcolor: 'action.hover',
              },
            }}
            onClick={triggerFileInput}
          >
            <PhotoCamera sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              点击或拖拽照片到这里
            </Typography>
            <Typography variant="body2" color="text.secondary">
              支持 JPG、PNG、WEBP 等格式
            </Typography>
          </Box>
        );

      case 'loading':
        return (
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <CircularProgress size={64} />
            <Typography variant="h6" sx={{ mt: 2 }}>
              正在解析照片信息...
            </Typography>
          </Box>
        );

      case 'error':
        return (
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <Alert severity="error" sx={{ mb: 2 }}>
              {photoState.error || '解析照片时发生错误'}
            </Alert>
            <Button
              variant="outlined"
              onClick={onRefresh}
              startIcon={<RefreshIcon />}
            >
              重试
            </Button>
          </Box>
        );

      case 'success':
        return (
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                照片信息
              </Typography>
              {photoState.photoInfo?.uri && (
                <Box sx={{ mb: 3 }}>
                  <img
                    src={photoState.photoInfo.uri}
                    alt="Preview"
                    style={{
                      width: '100%',
                      maxWidth: '300px',
                      height: 'auto',
                      borderRadius: 8,
                      objectFit: 'cover',
                    }}
                  />
                </Box>
              )}
              <Grid container spacing={1}>
                {photoState.photoInfo?.readExifInfoList.map((tag, index) => (
                  <Grid 
                    item 
                    xs={12} 
                    sm={6} 
                    md={4} 
                    lg={3} 
                    key={index}
                    sx={{ 
                      display: 'flex',
                      alignItems: 'flex-start',
                      p: 1,
                      '&:hover': {
                        bgcolor: 'action.hover',
                        borderRadius: 1,
                      }
                    }}
                  >
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="body2" color="text.secondary" noWrap>
                        {tag.name}
                      </Typography>
                      <Typography 
                        variant="body1" 
                        fontWeight="medium"
                        sx={{ wordBreak: 'break-word' }}
                      >
                        {tag.value}
                      </Typography>
                    </Box>
                  </Grid>
                ))}
              </Grid>
            </CardContent>
          </Card>
        );
    }
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <AppBar position="static" sx={{ boxShadow: 1 }}>
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            PhotoChecker
          </Typography>
          <IconButton color="inherit" onClick={onAboutClick}>
            <InfoIcon />
          </IconButton>
        </Toolbar>
      </AppBar>

      <Container maxWidth="md" sx={{ flex: 1, py: 4 }}>
        {renderContent()}
      </Container>

      <Fab
        color="primary"
        aria-label="选择照片"
        sx={{
          position: 'fixed',
          bottom: 24,
          right: 24,
          zIndex: 1000,
        }}
        onClick={triggerFileInput}
      >
        <PhotoCamera />
      </Fab>
    </Box>
  );
};

export default Home;