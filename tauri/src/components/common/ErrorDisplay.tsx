import { AlertCircle, RefreshCw } from "lucide-react";
import { Button } from "react-daisyui";

interface ErrorDisplayProps {
  error: string;
  onRetry?: () => void;
  retryText?: string;
}

export const ErrorDisplay = ({
  error,
  onRetry,
  retryText = "重试"
}: ErrorDisplayProps) => {
  return (
    <div className="flex flex-col items-center justify-center space-y-4 p-8">
      <div className="flex items-center space-x-2 text-error">
        <AlertCircle className="w-6 h-6" />
        <h3 className="text-lg font-semibold">出错了</h3>
      </div>

      <p className="text-center text-muted-foreground max-w-md">
        {error}
      </p>

      {onRetry && (
        <Button
          onClick={onRetry}
          className="btn-primary"
        >
          <RefreshCw className="w-4 h-4 mr-2" />
          {retryText}
        </Button>
      )}
    </div>
  );
};