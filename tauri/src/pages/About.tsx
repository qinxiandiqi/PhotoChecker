import React from 'react';
import {
  AppBar,
  Container,
  Toolbar,
  Typography,
  Card,
  CardContent,
  Box,
  Link,
  IconButton,
  List,
  ListItem,
  ListItemText,
} from '@mui/material';
import {
  ArrowBack as ArrowBackIcon,
  GitHub as GitHubIcon,
  Mail as MailIcon,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';

interface AboutProps {
  onBack: () => void;
}

const About: React.FC<AboutProps> = ({ onBack }) => {
  const navigate = useNavigate();

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <AppBar position="static" sx={{ boxShadow: 1 }}>
        <Toolbar>
          <IconButton
            color="inherit"
            edge="start"
            onClick={onBack}
            sx={{ mr: 1 }}
          >
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" component="div">
            关于
          </Typography>
        </Toolbar>
      </AppBar>

      <Container maxWidth="md" sx={{ flex: 1, py: 4 }}>
        <Card sx={{ mb: 4 }}>
          <CardContent>
            <Typography variant="h4" gutterBottom>
              PhotoChecker
            </Typography>
            <Typography variant="h6" color="primary" gutterBottom>
              EXIF 信息查看器
            </Typography>
            <Typography variant="body1" paragraph>
              一个轻量级的照片 EXIF 元数据查看工具，帮助您了解照片的技术细节。
            </Typography>
            <Typography variant="body2" color="text.secondary">
              版本: 1.0.0
            </Typography>
          </CardContent>
        </Card>

        <Card sx={{ mb: 4 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              功能特点
            </Typography>
            <List>
              <ListItem>
                <ListItemText primary="查看照片 EXIF 信息" />
              </ListItem>
              <ListItem>
                <ListItemText primary="支持多种图片格式" />
              </ListItem>
              <ListItem>
                <ListItemText primary="响应式界面设计" />
              </ListItem>
              <ListItem>
                <ListItemText primary="跨平台支持" />
              </ListItem>
            </List>
          </CardContent>
        </Card>

        <Card sx={{ mb: 4 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              技术栈
            </Typography>
            <List>
              <ListItem>
                <ListItemText primary="React 19" />
              </ListItem>
              <ListItem>
                <ListItemText primary="Material UI 5" />
              </ListItem>
              <ListItem>
                <ListItemText primary="Tauri 2" />
              </ListItem>
              <ListItem>
                <ListItemText primary="Vite" />
              </ListItem>
              <ListItem>
                <ListItemText primary="TypeScript" />
              </ListItem>
            </List>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              联系方式
            </Typography>
            <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
              <Link
                href="https://github.com/qinxiandiqi/PhotoChecker"
                target="_blank"
                rel="noopener noreferrer"
                color="text.secondary"
              >
                <IconButton color="primary" aria-label="GitHub">
                  <GitHubIcon />
                </IconButton>
                GitHub
              </Link>
              <Link
                href="mailto:contact@example.com"
                color="text.secondary"
              >
                <IconButton color="primary" aria-label="Email">
                  <MailIcon />
                </IconButton>
                Email
              </Link>
            </Box>
            <Typography variant="body2" color="text.secondary">
              欢迎提交问题和建议
            </Typography>
          </CardContent>
        </Card>
      </Container>
    </Box>
  );
};

export default About;